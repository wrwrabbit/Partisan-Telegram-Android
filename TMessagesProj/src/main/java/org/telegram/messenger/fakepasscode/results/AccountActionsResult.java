package org.telegram.messenger.fakepasscode.results;

import org.telegram.messenger.UserConfig;
import org.telegram.messenger.fakepasscode.AccountIdHashUtils;
import org.telegram.tgnet.TLRPC;

public class AccountActionsResult {
    public RemoveChatsResult removeChatsResult;
    public TelegramMessageResult telegramMessageResult;
    public String fakePhoneNumber;
    public String idHash;
    public HideAccountResult hideAccountResult;

    public boolean isHidden(boolean strictHiding) {
        return hideAccountResult != null && (!strictHiding || hideAccountResult.strictHiding);
    }

    public void updateIdHash(int accountNum, String salt) {
        idHash = calculateIdHash(accountNum, salt);
    }

    public static String calculateIdHash(int accountNum, String salt) {
        UserConfig userConfig = UserConfig.getInstance(accountNum);
        if (!userConfig.isClientActivated()) {
            return null;
        }
        TLRPC.User user = userConfig.getCurrentUser();
        if (user == null || user.phone == null) {
            return null;
        }
        try {
            return AccountIdHashUtils.calculateIdHash(user, salt);
        } catch (Exception ignore) {
            return null;
        }
    }

    public static AccountActionsResult merge(AccountActionsResult first, AccountActionsResult second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        AccountActionsResult result = new AccountActionsResult();
        result.removeChatsResult = first.removeChatsResult != null ? first.removeChatsResult : second.removeChatsResult;
        result.telegramMessageResult = first.telegramMessageResult != null ? first.telegramMessageResult : second.telegramMessageResult;
        result.fakePhoneNumber = first.fakePhoneNumber != null ? first.fakePhoneNumber : second.fakePhoneNumber;
        result.idHash = first.idHash != null ? first.idHash : second.idHash;
        result.hideAccountResult = first.hideAccountResult != null ? first.hideAccountResult : second.hideAccountResult;
        return result;
    }
}
