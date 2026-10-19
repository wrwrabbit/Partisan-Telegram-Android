package org.telegram.messenger.fakepasscode.results;

import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.fakepasscode.ChatFilter;

public class HideEncryptedChatsFromEncryptedGroups implements ChatFilter {
    private final int account;

    public HideEncryptedChatsFromEncryptedGroups(int account) {
        this.account = account;
    }

    @Override
    public boolean isRemoveNewMessagesFromChat(long chatId) {
        return false;
    }

    @Override
    public boolean isHideChat(long chatId, boolean strictHiding) {
        if (strictHiding) {
            return false;
        }
        if (DialogObject.isEncryptedDialog(chatId)) {
            int encryptedChatId = DialogObject.getEncryptedChatId(chatId);
            if (getMessagesController().getEncryptedGroupIdByInnerEncryptedChatId(encryptedChatId) != null) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isHideFolder(int folderId) {
        return false;
    }

    private MessagesController getMessagesController() {
        return MessagesController.getInstance(account);
    }
}
