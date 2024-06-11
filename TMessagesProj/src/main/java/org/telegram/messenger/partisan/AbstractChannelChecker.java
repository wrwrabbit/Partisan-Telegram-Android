package org.telegram.messenger.partisan;

import androidx.annotation.Nullable;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.fakepasscode.FakePasscodeUtils;
import org.telegram.messenger.partisan.verification.VerificationRepository;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public abstract class AbstractChannelChecker implements NotificationCenter.NotificationCenterDelegate {
    private final int MESSAGES_COUNT_PER_LOAD = 50;
    private final int classGuid;
    protected final int currentAccount;
    private Integer lastCheckedMessageId;
    private boolean updatesChecked = false;
    private boolean isLastMessageLoaded = false;
    private boolean usernameResolved = false;

    protected abstract long getChannelId();
    protected abstract String getChannelUsername();
    protected abstract void processChannelMessages(List<MessageObject> messages);
    protected abstract void messagesLoadingError();


    protected AbstractChannelChecker(int currentAccount, Integer lastCheckedMessageId) {
        this.currentAccount = currentAccount;
        this.lastCheckedMessageId = lastCheckedMessageId;
        classGuid = ConnectionsManager.generateClassGuid();
    }

    protected void checkUpdate() {
        AndroidUtilities.runOnUIThread(() -> {
            updatesChecked = false;
            getNotificationCenter().addObserver(this, NotificationCenter.messagesDidLoad);
            getNotificationCenter().addObserver(this, NotificationCenter.loadingMessagesFailed);
            loadMessages(true, 0);
        });
    }

    protected void removeObservers() {
        if (!updatesChecked) {
            getNotificationCenter().removeObserver(this, NotificationCenter.messagesDidLoad);
            getNotificationCenter().removeObserver(this, NotificationCenter.loadingMessagesFailed);
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.messagesDidLoad) {
            processMessagesDidLoad(args);
        } else if (id == NotificationCenter.loadingMessagesFailed) {
            processLoadingMessagesFailed(args);
        }
    }

    private void processMessagesDidLoad(Object[] args) {
        if (FakePasscodeUtils.isFakePasscodeActivated() || (Long) args[0] != getChannelId()) {
            return;
        }
        if (!isLastMessageLoaded) {
            lastMessageLoaded(args);
        } else {
            channelMessagesLoaded(args);
        }
    }

    private void lastMessageLoaded(Object[] args) {
        isLastMessageLoaded = true;
        loadMessages(false, (int)args[5]);
    }

    private void loadMessages(boolean testLoad, int lastMessageId) {
        int count = testLoad ? 1 : MESSAGES_COUNT_PER_LOAD;
        int loadType = needLoadAllMessagesFromChannel() ? 2 : 0;
        int lastMessageIdFinal = needLoadAllMessagesFromChannel() ? 0 : lastMessageId;
        int maxId = needLoadAllMessagesFromChannel() ? lastCheckedMessageId + MESSAGES_COUNT_PER_LOAD : 0;
        getMessagesController().loadMessages(getChannelId(), 0, false,
                count, maxId, 0, false, 0, classGuid,
                loadType, lastMessageIdFinal, 0, 0, 0, 1, false);
    }

    private boolean needLoadAllMessagesFromChannel() {
        return lastCheckedMessageId != null;
    }

    private void channelMessagesLoaded(Object[] args) {
        updatesChecked = true;
        getNotificationCenter().removeObserver(this, NotificationCenter.messagesDidLoad);
        getNotificationCenter().removeObserver(this, NotificationCenter.loadingMessagesFailed);
        ArrayList<MessageObject> messages = (ArrayList<MessageObject>) args[2];
        processChannelMessages(messages);

        if (needLoadAllMessagesFromChannel()) {
            int newLastMessageId = Math.max(getMaxMessageId(messages), lastCheckedMessageId);
            boolean isEnd = newLastMessageId == lastCheckedMessageId;
            lastCheckedMessageId = newLastMessageId;
            if (isEnd) {
                removeObservers();
            } else {
                loadMessages(false, (int)args[5]);
            }
        }
    }

    protected static List<MessageObject> sortMessageById(List<MessageObject> messages) {
        return messages.stream()
                .sorted(Comparator.comparingInt(MessageObject::getId))
                .collect(Collectors.toList());
    }

    protected static int getMaxMessageId(List<MessageObject> messages) {
        return messages.stream()
                .map(m -> m.messageOwner.id)
                .max(Integer::compareTo)
                .orElse(0);
    }

    private void processLoadingMessagesFailed(Object[] args) {
        if ((int) args[0] == classGuid
                && args.length >= 2
                && !usernameResolved
                && !FakePasscodeUtils.isFakePasscodeActivated()
                && validateFailedRequest(args[1])) {
            resolveUsername();
        }
    }

    private boolean validateFailedRequest(Object req) {
        TLRPC.InputPeer peer = getPeerFromRequest(req);
        if (peer == null) {
            return false;
        }
        long channelId = peer.channel_id != 0 ? peer.channel_id : peer.chat_id;
        return Math.abs(channelId) == Math.abs(getChannelId());
    }

    private TLRPC.InputPeer getPeerFromRequest(Object req) {
        if (req instanceof TLRPC.TL_messages_getPeerDialogs) {
            return getPeerFromGetPeerDialogsRequest((TLRPC.TL_messages_getPeerDialogs) req);
        } else if (req instanceof TLRPC.TL_messages_getHistory) {
            return ((TLRPC.TL_messages_getHistory)req).peer;
        } else {
            return null;
        }
    }

    private static TLRPC.InputPeer getPeerFromGetPeerDialogsRequest(TLRPC.TL_messages_getPeerDialogs req) {
        if (req.peers.isEmpty()) {
            return null;
        }
        TLRPC.InputDialogPeer inputDialogPeer = req.peers.get(0);
        if (!(inputDialogPeer instanceof TLRPC.TL_inputDialogPeer)) {
            return null;
        }
        return ((TLRPC.TL_inputDialogPeer) inputDialogPeer).peer;
    }

    private void resolveUsername() {
        TLRPC.TL_contacts_resolveUsername req = new TLRPC.TL_contacts_resolveUsername();
        req.username = getChannelUsername();
        getConnectionsManager().sendRequest(req, this::usernameResolvingResponseReceived);
    }

    protected void usernameResolvingResponseReceived(TLObject response, TLRPC.TL_error error) {
        usernameResolved = true;
        AndroidUtilities.runOnUIThread(() -> {
            getNotificationCenter().removeObserver(this, NotificationCenter.loadingMessagesFailed);
            if (response != null) {
                TLRPC.TL_contacts_resolvedPeer res = (TLRPC.TL_contacts_resolvedPeer) response;
                putUsersAndChats(res);
                loadMessages(true, 0);
            } else {
                removeObservers();
                messagesLoadingError();
            }
        });
    }

    private void putUsersAndChats(TLRPC.TL_contacts_resolvedPeer res) {
        getMessagesController().putUsers(res.users, false);
        getMessagesController().putChats(res.chats, false);
        getMessagesStorage().putUsersAndChats(res.users, res.chats, true, true);
    }

    protected AccountInstance getAccountInstance() {
        return AccountInstance.getInstance(currentAccount);
    }

    private NotificationCenter getNotificationCenter() {
        return getAccountInstance().getNotificationCenter();
    }

    private MessagesController getMessagesController() {
        return getAccountInstance().getMessagesController();
    }

    private MessagesStorage getMessagesStorage() {
        return getAccountInstance().getMessagesStorage();
    }

    protected ConnectionsManager getConnectionsManager() {
        return getAccountInstance().getConnectionsManager();
    }
}
