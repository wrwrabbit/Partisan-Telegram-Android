package org.telegram.messenger.fakepasscode;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.SharedConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TerminateOtherSessionsAction extends AccountAction {
    private int mode = 0;
    private List<Long> sessions = new ArrayList<>();

    public TerminateOtherSessionsAction() {}

    public TerminateOtherSessionsAction(int accountNum) {
        this.accountNum = accountNum;
    }

    @Override
    public void execute(FakePasscode fakePasscode) {
        if (mode == SelectionMode.SELECTED) {
            terminateSelectedSessions(fakePasscode);
        } else if (mode == SelectionMode.EXCEPT_SELECTED) {
            terminateExceptSelectedSessions(fakePasscode);
        }
    }

    private void terminateSelectedSessions(FakePasscode fakePasscode) {
    }

    private void terminateExceptSelectedSessions(FakePasscode fakePasscode) {
    }

    public List<Long> getSessions() {
        return sessions;
    }

    public void setSessions(List<Long> sessions) {
        this.sessions = sessions;
        SharedConfig.saveConfig();
    }

    public int getMode() {
        return mode;
    }

    public void setMode(int mode) {
        this.mode = mode;
        SharedConfig.saveConfig();
    }

    @Override
    public void migrate() {
        super.migrate();
        mode = 1;
        sessions = new ArrayList<>();
    }
}
