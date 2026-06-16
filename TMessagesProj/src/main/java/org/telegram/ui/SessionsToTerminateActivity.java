package org.telegram.ui;

import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.AlertDialog;
import android.content.Context;
import android.view.View;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.fakepasscode.AccountActions;

import java.util.List;

public class SessionsToTerminateActivity extends CheckableSessionsActivity {
    private final AccountActions actions;

    SessionsToTerminateActivity(AccountActions actions) {
        super(actions.getAccountNum());
        this.actions = actions;
    }


    @Override
    public View createView(Context context) {
        if (SharedConfig.showSessionsTerminateActionWarning) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setTitle(LocaleController.getString(R.string.TerminateOtherSessionsWarningTitle));
            builder.setMessage(LocaleController.getString(R.string.TerminateOtherSessionsWarningMessage));
            builder.setPositiveButton(LocaleController.getString(R.string.OK), null);
            builder.setNegativeButton(LocaleController.getString(R.string.DoNotShowAgain), (dialog, whichButton) -> {
                SharedConfig.showSessionsTerminateActionWarning = false;
                SharedConfig.saveConfig();
            });
            showDialog(builder.create());
        }
        return super.createView(context);
    }

    @Override
    protected List<Long> loadCheckedSessions() {
        return actions.getTerminateOtherSessionsAction().getSessions();
    }

    @Override
    protected void saveCheckedSession(List<Long> checkedSessions) {
        actions.getTerminateOtherSessionsAction().setSessions(checkedSessions);
    }

    @Override
    protected String getTitle() {
        return LocaleController.getString(R.string.SessionsToTerminate);
    }

    @Override
    public void didSelectedMode(int mode) {
        actions.getTerminateOtherSessionsAction().setMode(mode);
        super.didSelectedMode(mode);
    }

    @Override
    public int getSelectedMode() {
        return actions.getTerminateOtherSessionsAction().getMode();
    }
}
