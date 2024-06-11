package org.telegram.messenger.partisan.verification;

import org.telegram.messenger.MessageObject;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.partisan.AbstractChannelChecker;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;

public class VerificationUpdatesChecker extends AbstractChannelChecker {
    private final VerificationStorage storage;

    public VerificationUpdatesChecker(int currentAccount, VerificationStorage storage) {
        super(currentAccount, storage.lastCheckedMessageId);
        this.storage = storage;
    }

    public static void checkUpdate(int currentAccount, boolean force) {
        if (UserConfig.getInstance(currentAccount).isClientActivated()) {
            for (VerificationStorage storage : VerificationRepository.getInstance().getStorages()) {
                if (!force && System.currentTimeMillis() - storage.nextCheckTime <= 0) {
                    continue;
                }
                VerificationUpdatesChecker checker = new VerificationUpdatesChecker(currentAccount, storage);
                checker.checkUpdate();
            }
        }
    }

    @Override
    protected void checkUpdate() {
        VerificationRepository.getInstance().saveNextCheckTime(storage.chatId, System.currentTimeMillis());
        super.checkUpdate();
    }

    @Override
    protected long getChannelId() {
        return storage.chatId;
    }

    @Override
    protected String getChannelUsername() {
        return storage.chatUsername;
    }

    @Override
    protected void processChannelMessages(List<MessageObject> messages) {
        List<VerificationChatInfo> chatsToAdd = new ArrayList<>();
        List<VerificationChatInfo> chatsToRemove = new ArrayList<>();
        VerificationMessageParser parser = new VerificationMessageParser();
        List<MessageObject> sortedMessages = sortMessageById(messages);
        for (MessageObject message : sortedMessages) {
            if (message.messageOwner.id <= storage.lastCheckedMessageId) {
                continue;
            }
            VerificationMessageParser.ParsingResult result = parser.parseMessage(message);
            if (result != null) {
                chatsToAdd.removeIf(c -> listContains(result.chatsToRemove, c));
                chatsToRemove.removeIf(c -> listContains(result.chatsToAdd, c));
                chatsToAdd.addAll(result.chatsToAdd);
                chatsToRemove.addAll(result.chatsToRemove);
            }
        }
        VerificationRepository.getInstance().putChats(storage.chatId, chatsToAdd);
        VerificationRepository.getInstance().deleteChats(storage.chatId, chatsToRemove);

        int lastMessageId = Math.max(getMaxMessageId(messages), storage.lastCheckedMessageId);
        VerificationRepository.getInstance().saveLastCheckedMessageId(storage.chatId, lastMessageId);
    }

    private boolean listContains(List<VerificationChatInfo> list, VerificationChatInfo chatInfo) {
        return list.stream().anyMatch(c -> c.chatId == chatInfo.chatId);
    }

    @Override
    protected void messagesLoadingError() {}

    @Override
    protected void usernameResolvingResponseReceived(TLObject response, TLRPC.TL_error error) {
        if (response != null) {
            long chatId = peerToChatId(((TLRPC.TL_contacts_resolvedPeer)response).peer);
            VerificationRepository.getInstance().saveRepositoryChatId(storage.chatUsername, -chatId);
            storage.chatId = -chatId;
        }
        super.usernameResolvingResponseReceived(response, error);
    }

    private long peerToChatId(TLRPC.Peer peer) {
        return peer.channel_id != 0 ? peer.channel_id : peer.chat_id;
    }
}