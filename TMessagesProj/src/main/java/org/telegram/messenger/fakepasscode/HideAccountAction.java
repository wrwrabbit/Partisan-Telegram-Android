package org.telegram.messenger.fakepasscode;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.fakepasscode.results.HideAccountResult;

@FakePasscodeSerializer.ToggleSerialization
public class HideAccountAction extends AccountAction {
    public boolean strictHiding;

    @Override
    public void execute(FakePasscode fakePasscode) {
        if (fakePasscode.replaceOriginalPasscode) {
            return;
        }
        fakePasscode.actionsResult.hiddenAccountEntries.add(new HideAccountResult(accountNum, strictHiding));
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.accountHidingChanged);
        AccountInstance.getInstance(accountNum).getNotificationsController().removeAllNotifications();
    }
}
