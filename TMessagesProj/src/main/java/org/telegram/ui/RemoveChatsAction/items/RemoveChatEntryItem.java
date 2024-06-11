package org.telegram.ui.RemoveChatsAction.items;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.fakepasscode.RemoveChatsAction;
import org.telegram.tgnet.TLObject;

public class RemoveChatEntryItem extends Item {
    private final RemoveChatsAction.RemoveChatEntry removeChatEntry;

    RemoveChatEntryItem(int accountNum, RemoveChatsAction.RemoveChatEntry removeChatEntry) {
        super(accountNum);
        this.removeChatEntry = removeChatEntry;
    }

    @Override
    public TLObject getProfileObject() {
        return null;
    }

    @Override
    public long getId() {
        return removeChatEntry.chatId;
    }

    @Override
    protected String getName() {
        return removeChatEntry.title;
    }

    @Override
    public String getUsername() {
        return null;
    }

    @Override
    protected CharSequence generateSearchName(String query) {
        return AndroidUtilities.generateSearchName(removeChatEntry.title, null, query);
    }
}
