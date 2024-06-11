package org.telegram.ui.RemoveChatsAction.items;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

class UserItem extends AbstractUserItem {
    UserItem(int accountNum, TLRPC.User user) {
        super(accountNum, user);
    }

    @Override
    public TLObject getProfileObject() {
        return user;
    }

    @Override
    public long getId() {
        return user.id;
    }

    @Override
    public String getAlternativeName() {
        if (UserObject.isReplyUser(user)) {
            return LocaleController.getString("RepliesTitle", R.string.RepliesTitle);
        } else if (user.self) {
            return LocaleController.getString("SavedMessages", R.string.SavedMessages);
        } else {
            return null;
        }
    }

    @Override
    public String getDisplayName() {
        if (getAlternativeName() != null) {
            return getAlternativeName();
        } else {
            return super.getDisplayName();
        }
    }

    @Override
    protected CharSequence generateSearchName(String query) {
        if (getAlternativeName() != null) {
            return AndroidUtilities.generateSearchName(getAlternativeName(), null, query);
        } else {
            return super.generateSearchName(query);
        }
    }

    @Override
    public boolean isSelf() {
        return UserObject.isUserSelf(user);
    }

    @Override
    public CharSequence getStatus() {
        if (isBlocked()) {
            return LocaleController.getString(R.string.BlockedUsers);
        } else {
            return super.getStatus();
        }
    }

    private boolean isBlocked() {
        return getMessagesController().getUnfilteredBlockedPeers().get(user.id) == 1;
    }
}
