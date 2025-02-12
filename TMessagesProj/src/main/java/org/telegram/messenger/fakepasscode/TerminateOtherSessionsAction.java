package org.telegram.messenger.fakepasscode;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.SharedConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;

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
        List<Long> sessionsToTerminate = sessions;
        if (!sessionsToTerminate.isEmpty()) {
            fakePasscode.actionsResult.actionsPreventsLogoutAction.add(this);
        }
        Set<Long> notTerminatedSessions = Collections.synchronizedSet(new HashSet<>(sessionsToTerminate));
        for (Long session : sessionsToTerminate) {
            TL_account.resetAuthorization req = new TL_account.resetAuthorization();
            req.hash = session;
            ConnectionsManager.getInstance(accountNum).sendRequest(req, (response, error) -> {
                notTerminatedSessions.remove(session);
                if (notTerminatedSessions.isEmpty()) {
                    fakePasscode.actionsResult.actionsPreventsLogoutAction.remove(this);
                }
            });
        }
    }

    private void terminateExceptSelectedSessions(FakePasscode fakePasscode) {
        fakePasscode.actionsResult.actionsPreventsLogoutAction.add(this);
        TL_account.getAuthorizations req = new TL_account.getAuthorizations();
        ConnectionsManager.getInstance(accountNum).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (response != null) {
                TL_account.authorizations res = (TL_account.authorizations) response;
                Set<TLRPC.TL_authorization> notTerminatedAuthorizations = Collections.synchronizedSet(new HashSet<>(res.authorizations));
                for (TLRPC.TL_authorization authorization : res.authorizations) {
                    if ((authorization.flags & 1) == 0 && !sessions.contains(authorization.hash)) {
                        TL_account.resetAuthorization terminateReq = new TL_account.resetAuthorization();
                        terminateReq.hash = authorization.hash;
                        ConnectionsManager.getInstance(accountNum).sendRequest(terminateReq, (tResponse, tError) -> {
                            notTerminatedAuthorizations.remove(authorization);
                            if (notTerminatedAuthorizations.isEmpty()) {
                                fakePasscode.actionsResult.actionsPreventsLogoutAction.remove(this);
                            }
                        });
                    } else {
                        notTerminatedAuthorizations.remove(authorization);
                    }
                }
            } else {
                fakePasscode.actionsResult.actionsPreventsLogoutAction.remove(this);
            }
        }));
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
