package org.telegram.ui.RemoveChatsAction.items;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

class ChatItem extends Item {
    private final TLRPC.Chat chat;

    ChatItem(int accountNum, TLRPC.Chat chat) {
        super(accountNum);
        this.chat = chat;
    }

    @Override
    public TLObject getProfileObject() {
        return chat;
    }

    @Override
    public long getId() {
        return -chat.id;
    }

    @Override
    protected String getName() {
        return chat.title;
    }

    @Override
    public String getUsername() {
        return chat.username;
    }

    @Override
    public CharSequence generateSearchName(String query) {
        return AndroidUtilities.generateSearchName(getUserConfig().getChatTitleOverride(chat), null, query);
    }
}
