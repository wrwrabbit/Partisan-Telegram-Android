    package org.telegram.messenger.fakepasscode;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.fakepasscode.results.HideAccountResult;

    @FakePasscodeSerializer.ToggleSerialization
public class LogOutAction extends AccountAction {
    private static final int WAIT_TIME = 0;

    public LogOutAction() {}

    public LogOutAction(int accountNum) {
        this.accountNum = accountNum;
    }

    @Override
    public void execute(FakePasscode fakePasscode) {
        fakePasscode.actionsResult.unhideAccount(accountNum);
        MessagesController.getInstance(accountNum).performLogout(1);
        if (fakePasscode.replaceOriginalPasscode) {
            removeAccountFromOtherPasscodes();
        }
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.appDidLogoutByAction, accountNum);
    }

    private void removeAccountFromOtherPasscodes() {
        for (FakePasscode fakePasscode : SharedConfig.fakePasscodes) {
            fakePasscode.removeAccountActions(accountNum);
        }
        SharedConfig.saveConfig();
    }

    public void hideAccount(FakePasscode fakePasscode) {
        fakePasscode.actionsResult.addHideAccountResult(new HideAccountResult(accountNum, true));
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.accountHidingChanged);
        AccountInstance.getInstance(accountNum).getNotificationsController().removeAllNotifications();
    }
}
