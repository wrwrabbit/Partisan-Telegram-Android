package org.telegram.ui.RemoveChatsAction.items;

import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessagesController;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

public class EncryptedChatItem extends AbstractUserItem {
    private final TLRPC.EncryptedChat encryptedChat;

    EncryptedChatItem(int accountNum, TLRPC.EncryptedChat encryptedChat) {
        super(accountNum, MessagesController.getInstance(accountNum).getUser(encryptedChat.user_id));
        this.encryptedChat = encryptedChat;
    }

    @Override
    public TLObject getProfileObject() {
        return user;
    }

    @Override
    public long getId() {
        return DialogObject.makeEncryptedDialogId(encryptedChat.id);
    }

    @Override
    public boolean shouldBeEditedToo(Item selectedItem) {
        return encryptedChat.user_id == selectedItem.getId();
    }
}
