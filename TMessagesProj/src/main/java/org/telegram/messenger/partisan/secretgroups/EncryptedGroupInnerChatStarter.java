package org.telegram.messenger.partisan.secretgroups;

import static org.telegram.messenger.partisan.secretgroups.EncryptedGroupState.CREATING_ENCRYPTED_CHATS;
import static org.telegram.messenger.partisan.secretgroups.EncryptedGroupState.INITIALIZED;
import static org.telegram.messenger.partisan.secretgroups.EncryptedGroupState.NEW_MEMBER_WAITING_SECONDARY_CHAT_CREATION;
import static org.telegram.messenger.partisan.secretgroups.EncryptedGroupState.WAITING_SECONDARY_CHAT_CREATION;

import android.os.SystemClock;
import android.util.Pair;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.SecretChatHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.partisan.AccountControllersProvider;
import org.telegram.messenger.partisan.Utils;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.LaunchActivity;

import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

public class EncryptedGroupInnerChatStarter implements AccountControllersProvider {
    private final int accountNum;
    private volatile SecretChatStartDelegate currentDelegate;
    private long floodWaitUntil = 0;

    private static final EncryptedGroupInnerChatStarter[] Instances = new EncryptedGroupInnerChatStarter[UserConfig.MAX_ACCOUNT_COUNT];

    private EncryptedGroupInnerChatStarter(int accountNum) {
        this.accountNum = accountNum;
    }

    public synchronized static EncryptedGroupInnerChatStarter getInstance(int accountNum) {
        if (Instances[accountNum] == null) {
            Instances[accountNum] = new EncryptedGroupInnerChatStarter(accountNum);
        }
        return Instances[accountNum];
    }

    public static void checkForAllAccounts() {
        Utils.foreachActivatedAccountInstance(accountInstance ->
                getInstance(accountInstance.getCurrentAccount()).startNextInnerChatIfNeeded()
        );
    }

    private synchronized void startNextInnerChatIfNeeded() {
        if (LaunchActivity.instance == null || getSecretChatHelper().isStartingSecretChat() || currentDelegate != null) {
            return;
        }
        if (floodWaitUntil > 0 && floodWaitUntil > SystemClock.elapsedRealtime()) {
            return;
        }
        Pair<InnerEncryptedChat, EncryptedGroup> pair = getNextUninitializedInnerChat();
        if (pair != null) {
            InnerEncryptedChat uninitializedInnerChat = pair.first;
            EncryptedGroup encryptedGroup = pair.second;
            TLRPC.User user = getMessagesController().getUser(uninitializedInnerChat.getUserId());
            log(encryptedGroup, "Start secondary inner chat with a user.");
            currentDelegate = new SecretChatStartDelegate(encryptedGroup);
            Utilities.globalQueue.postRunnable(() ->
                    getSecretChatHelper().startSecretChat(LaunchActivity.instance, user, currentDelegate)
            );
        }
    }

    private Pair<InnerEncryptedChat, EncryptedGroup> getNextUninitializedInnerChat() {
        Pair<InnerEncryptedChat, EncryptedGroup> uninitializedInnerChat = getNextSecondaryChat();
        if (uninitializedInnerChat == null) {
            uninitializedInnerChat = getNextPrimaryChat();
        }
        if (uninitializedInnerChat == null) {
            uninitializedInnerChat = getNextNewMemberChat();
        }
        return uninitializedInnerChat;
    }

    private Pair<InnerEncryptedChat, EncryptedGroup> getNextSecondaryChat() {
        for (EncryptedGroup encryptedGroup : getEncryptedGroupsByStates(WAITING_SECONDARY_CHAT_CREATION, NEW_MEMBER_WAITING_SECONDARY_CHAT_CREATION)) {
            InnerEncryptedChat uninitializedInnerChat = encryptedGroup.getInnerChats().stream()
                    // Users with smaller ids will initialize chats with users with bigger ids. New members will initialize chats with all other users.
                    .filter(c -> !c.getEncryptedChatId().isPresent() && (c.getUserId() > getUserConfig().clientUserId || encryptedGroup.isInState(NEW_MEMBER_WAITING_SECONDARY_CHAT_CREATION)))
                    .findAny()
                    .orElse(null);
            if (uninitializedInnerChat != null) {
                return new Pair<>(uninitializedInnerChat, encryptedGroup);
            }
        }
        return null;
    }

    private Pair<InnerEncryptedChat, EncryptedGroup> getNextPrimaryChat() {
        return getFirstInnerEncryptedChatByState(CREATING_ENCRYPTED_CHATS, InnerEncryptedChatState.CREATING_ENCRYPTED_CHAT);
    }

    private Pair<InnerEncryptedChat, EncryptedGroup> getNextNewMemberChat() {
        return getFirstInnerEncryptedChatByState(INITIALIZED, InnerEncryptedChatState.NEW_MEMBER_CREATING_ENCRYPTED_CHAT);
    }

    private List<EncryptedGroup> getEncryptedGroupsByStates(EncryptedGroupState... states) {
        return getMessagesController().getAllEncryptedGroups()
                .stream()
                .filter(group -> group.isInState(states))
                .collect(Collectors.toList());
    }

    private Pair<InnerEncryptedChat, EncryptedGroup> getFirstInnerEncryptedChatByState(EncryptedGroupState groupState, InnerEncryptedChatState innerChatState) {
        for (EncryptedGroup encryptedGroup : getEncryptedGroupsByStates(groupState)) {
            for (InnerEncryptedChat innerChat : encryptedGroup.getInnerChats()) {
                if (innerChat.isInState(innerChatState)) {
                    return new Pair<>(innerChat, encryptedGroup);
                }
            }
        }
        return null;
    }

    private class SecretChatStartDelegate implements SecretChatHelper.SecretChatStartDelegate {
        private final EncryptedGroup encryptedGroup;

        public SecretChatStartDelegate(EncryptedGroup encryptedGroup) {
            this.encryptedGroup = encryptedGroup;
        }

        @Override
        public void onComplete(TLRPC.EncryptedChat encryptedChat) {
            if (encryptedChat != null) {
                InnerEncryptedChat innerChat = encryptedGroup.getInnerChatByUserId(encryptedChat.user_id);
                innerChat.setEncryptedChatId(encryptedChat.id);
                if (encryptedGroup.isInState(CREATING_ENCRYPTED_CHATS)) {
                    onPrimaryChatStarted(innerChat);
                } else if (encryptedGroup.isInState(WAITING_SECONDARY_CHAT_CREATION, NEW_MEMBER_WAITING_SECONDARY_CHAT_CREATION)) {
                    onSecondaryChatStarted(innerChat);
                } else {
                    onMemberAdded(innerChat);
                }
            }
            currentDelegate = null;
        }

        private void onPrimaryChatStarted(InnerEncryptedChat innerChat) {
            log(encryptedGroup, "A primary inner chat with a user started.");
            innerChat.setState(InnerEncryptedChatState.NEED_SEND_INVITATION);
            getMessagesStorage().updateEncryptedGroupInnerChat(encryptedGroup.getInternalId(), innerChat);

            if (encryptedGroup.noneInnerChatsMatchState(InnerEncryptedChatState.CREATING_ENCRYPTED_CHAT)) {
                AndroidUtilities.runOnUIThread(() -> {
                    encryptedGroup.setState(EncryptedGroupState.WAITING_CONFIRMATION_FROM_MEMBERS);
                    getMessagesStorage().updateEncryptedGroup(encryptedGroup);
                    log(encryptedGroup, "Group created by owner.");
                });
            }
        }

        private void onSecondaryChatStarted(InnerEncryptedChat innerChat) {
            log(encryptedGroup, "A secondary inner chat with a user started.");
            innerChat.setState(InnerEncryptedChatState.NEED_SEND_SECONDARY_INVITATION);
            getMessagesStorage().updateEncryptedGroupInnerChat(encryptedGroup.getInternalId(), innerChat);

            getEncryptedGroupUtils().checkAllEncryptedChatsCreated(encryptedGroup);
        }

        private void onMemberAdded(InnerEncryptedChat innerChat) {
            log(encryptedGroup, "A member added.");
            innerChat.setState(InnerEncryptedChatState.NEW_MEMBER_NEED_SEND_INVITATION);
            getMessagesStorage().updateEncryptedGroupInnerChat(encryptedGroup.getInternalId(), innerChat);
        }

        @Override
        public void onError(TLRPC.TL_error error) {
            if (error.text.startsWith("FLOOD_WAIT")) {
                int floodWait = Utilities.parseInt(error.text);
                floodWaitUntil = SystemClock.elapsedRealtime() + (floodWait + 1) * 1000;
            }
            currentDelegate = null;
        }

        @Override
        public boolean allowShowingDialogs() {
            return false;
        }
    }

    @Override
    public int getAccountNum() {
        return accountNum;
    }

    public long getFloodWaitUntil() {
        return floodWaitUntil;
    }

    private void log(@Nullable EncryptedGroup encryptedGroup, String message) {
        getEncryptedGroupUtils().log(encryptedGroup, message);
    }
}
