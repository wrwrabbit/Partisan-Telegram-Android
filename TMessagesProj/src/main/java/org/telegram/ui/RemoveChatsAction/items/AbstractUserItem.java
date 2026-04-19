package org.telegram.ui.RemoveChatsAction.items;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;

abstract class AbstractUserItem extends Item {
    protected TLRPC.User user;

    AbstractUserItem(int accountNum, TLRPC.User user) {
        super(accountNum);
        this.user = user;
    }

    @Override
    protected String getName() {
        return user != null ? ContactsController.formatName(user.first_name, user.last_name) : LocaleController.getString(R.string.HiddenName);
    }

    @Override
    public String getUsername() {
        return user != null ? user.username : null;
    }

    @Override
    protected CharSequence generateSearchName(String query) {
        return user != null ? AndroidUtilities.generateSearchName(user.first_name, user.last_name, query) : LocaleController.getString(R.string.HiddenName);
    }

    @Override
    public String getDisplayName() {
        return UserObject.getUserName(user, accountNum);
    }
}
