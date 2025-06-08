package org.telegram.messenger.partisan.findmessages;

import com.google.common.collect.Lists;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.partisan.AccountControllersProvider;
import org.telegram.messenger.partisan.KnownChatUsernameResolver;
import org.telegram.messenger.partisan.PartisanLog;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ChatActivity;

import java.util.ArrayList;
import java.util.List;

class ChatMessagesDeleter implements AccountControllersProvider {
    interface Delegate {
        void chatProcessed(long chatId, boolean wasError);
    }

    private static final int MESSAGES_CHUNK_SIZE = 100;
    private final int accountNum;
    private final FindMessagesChatData chatData;
    private final Delegate delegate;

    ChatMessagesDeleter(int accountNum, FindMessagesChatData chatData, Delegate delegate) {
        this.accountNum = accountNum;
        this.chatData = chatData;
        this.delegate = delegate;
    }

    static void processChat(int accountNum, FindMessagesChatData chatData, Delegate delegate) {
        ChatMessagesDeleter deleter = new ChatMessagesDeleter(accountNum, chatData, delegate);
        deleter.processChatInternal();
    }

    private void processChatInternal() {
        if (isChatResolved(chatData.chatId)) {
            PartisanLog.d("[FindMessages] delete messages from chatId " + chatData.chatId);
            tryDeleteMessages();
        } else {
            getAccessToChat();
        }
    }

    private boolean isChatResolved(long chatId) {
        return getMessagesController().getDialog(chatId) != null;
    }

    private void getAccessToChat() {
        if (chatData.username != null) {
            resolveChatUsername();
        } else if (chatData.linkedChatId != null) {
            resolveLinkedChatUsername();
        }
    }

    private void resolveChatUsername() {
        PartisanLog.d("[FindMessages] chatId " + chatData.chatId + " not found. Resolve username: " + chatData.username);
        resolveUsername(chatData.username, chatData.chatId, success -> {
            if (success) {
                tryDeleteMessages();
            } else if (chatData.linkedChatId != null) {
                resolveLinkedChatUsername();
            } else {
                PartisanLog.d("[FindMessages] chatId " + chatData.chatId + " resolve username failed");
                fail();
            }
        });
    }

    private void resolveLinkedChatUsername() {
        PartisanLog.d("[FindMessages] chatId " + chatData.chatId + " not found. Resolve linked username: " + chatData.linkedUsername);
        resolveUsername(chatData.linkedUsername, chatData.linkedChatId, success -> {
            if (success) {
                loadFullLinkedChat();
            } else {
                PartisanLog.d("[FindMessages] chatId " + chatData.chatId + " resolve linked username failed");
                fail();
            }
        });
    }

    private void resolveUsername(String username, long chatId, KnownChatUsernameResolver.KnownChatUsernameResolverDelegate callback) {
        KnownChatUsernameResolver.resolveUsername(accountNum, username, chatId, callback);
    }

    private void loadFullLinkedChat() {
        TLRPC.TL_channels_getFullChannel request = new TLRPC.TL_channels_getFullChannel();
        request.channel = getMessagesController().getInputChannel(-chatData.linkedChatId);
        getConnectionsManager().sendRequest(request, (response, error) -> {
            if (response != null) {
                TLRPC.TL_messages_chatFull res = (TLRPC.TL_messages_chatFull) response;
                getMessagesStorage().putUsersAndChats(res.users, res.chats, true, false);
                tryDeleteMessages();
            } else {
                PartisanLog.d("[FindMessages] chatId " + chatData.chatId + " load full linked chat failed");
                fail();
            }
        });
    }

    private void tryDeleteMessages() {
        AndroidUtilities.runOnUIThread(() -> {
            getMessagesController().ensureMessagesLoaded(chatData.chatId, 0, new MessagesController.MessagesLoadedCallback() {
                @Override
                public void onMessagesLoaded(boolean fromCache) {
                    deleteMessages();
                }

                @Override
                public void onError() {
                    PartisanLog.d("[FindMessages] chatId " + chatData.chatId + " ensure messages loaded failed");
                    fail();
                }
            });
        });
    }

    private void deleteMessages() {
        AndroidUtilities.runOnUIThread(() -> {
            for (List<Integer> messagesChunk : Lists.partition(chatData.messageIds, MESSAGES_CHUNK_SIZE)) {
                deleteMessagesChunk(messagesChunk);
            }
            success();
        });
    }

    private void deleteMessagesChunk(List<Integer> messagesChunk) {
        PartisanLog.d(
                "[FindMessages] run deletion for " + chatData.chatId +
                        " for " + messagesChunk.size() + " messages");
        getMessagesController().deleteMessages(
                new ArrayList<>(messagesChunk), null, null,
                chatData.chatId, 0, true, ChatActivity.MODE_DEFAULT);
    }

    private void fail() {
        delegate.chatProcessed(chatData.chatId, true);
    }

    private void success() {
        delegate.chatProcessed(chatData.chatId, false);
    }

    @Override
    public int getAccountNum() {
        return accountNum;
    }
}
