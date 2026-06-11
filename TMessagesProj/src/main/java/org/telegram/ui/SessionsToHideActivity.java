package org.telegram.ui;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.fakepasscode.AccountActions;

import java.util.List;

public class SessionsToHideActivity extends CheckableSessionsActivity {
    private final AccountActions actions;

    SessionsToHideActivity(AccountActions actions) {
        super(actions.getAccountNum());
        this.actions = actions;
    }

    @Override
    protected List<Long> loadCheckedSessions() {
        return actions.getSessionsToHide().getSessions();
    }

    @Override
    protected void saveCheckedSession(List<Long> checkedSessions) {
        actions.setSessionsToHide(checkedSessions);
    }

    @Override
    protected String getTitle() {
        return LocaleController.getString(R.string.SessionsToHide);
    }

    @Override
    public void didSelectedMode(int mode) {
        actions.getSessionsToHide().setMode(mode);
        super.didSelectedMode(mode);
    }

    @Override
    public int getSelectedMode() {
        return actions.getSessionsToHide().getMode();
    }
}
