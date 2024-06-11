package org.telegram.messenger.fakepasscode;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.android.exoplayer2.util.Log;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.fakepasscode.results.ActionsResult;
import org.telegram.messenger.fakepasscode.results.RemoveChatsResult;
import org.telegram.messenger.partisan.Utils;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@JsonIgnoreProperties(ignoreUnknown=true)
public class FakePasscode {
    @JsonIgnore
    private final int CURRENT_PASSCODE_VERSION = 3;
    private int passcodeVersion = 0;

    @JsonProperty(value = "PLATFORM", access = JsonProperty.Access.READ_ONLY)
    public String getPlatform() {
        return "ANDROID";
    }

    public UUID uuid;
    public boolean allowLogin = true;
    public String name;
    @FakePasscodeSerializer.Ignore
    public String passcodeHash = "";
    public String activationMessage = "";
    public Integer badTriesToActivate;
    public Integer activateByTimerTime;
    public boolean passwordlessMode;
    public boolean passwordDisabled;
    public boolean activateByFingerprint;
    public boolean clearAfterActivation;
    @Deprecated
    public Boolean deleteOtherPasscodesAfterActivation;
    public DeleteOtherFakePasscodesAction deletePasscodesAfterActivation = new DeleteOtherFakePasscodesAction();
    public boolean replaceOriginalPasscode;

    public ClearCacheAction clearCacheAction = new ClearCacheAction();
    public ClearDownloadsAction clearDownloadsAction = new ClearDownloadsAction();
    public SmsAction smsAction = new SmsAction();
    public ClearProxiesAction clearProxiesAction = new ClearProxiesAction();

    @FakePasscodeSerializer.Ignore
    ActionsResult actionsResult = new ActionsResult();
    private Integer activationDate = null;
    boolean activated = false;

    public List<AccountActions> accountActions = Collections.synchronizedList(new ArrayList<>());

    public static FakePasscode create() {
        if (SharedConfig.fakePasscodes.isEmpty() && SharedConfig.fakePasscodeIndex != 1) {
            SharedConfig.fakePasscodeIndex = 1;
            AndroidUtilities.runOnUIThread(SharedConfig::saveConfig);
        }
        FakePasscode fakePasscode = new FakePasscode();
        fakePasscode.uuid = UUID.randomUUID();
        fakePasscode.name = LocaleController.getString("FakePasscode", R.string.FakePasscode) + " " + (SharedConfig.fakePasscodeIndex);
        fakePasscode.autoAddAccountHidings();
        return fakePasscode;
    }

    List<Action> actions()
    {
        List<Action> result = new ArrayList<>(Arrays.asList(clearCacheAction, clearDownloadsAction, smsAction));
        result.addAll(accountActions);
        result.add(clearProxiesAction);
        result.add(deletePasscodesAfterActivation);
        return result;
    }

    public AccountActions getAccountActions(int accountNum) {
        for (AccountActions actions : accountActions) {
            Integer actionsAccountNum = actions.getAccountNum();
            if (actionsAccountNum != null && actionsAccountNum == accountNum) {
                return actions;
            }
        }
        return null;
    }

    public AccountActions getOrCreateAccountActions(int accountNum) {
        for (AccountActions actions : accountActions) {
            Integer actionsAccountNum = actions.getAccountNum();
            if (actionsAccountNum != null && actionsAccountNum == accountNum) {
                return actions;
            }
        }
        AccountActions actions = new AccountActions();
        actions.setAccountNum(accountNum);
        accountActions.add(actions);
        return actions;
    }

    public void removeAccountActions(int accountNum) {
        accountActions.removeIf(a -> a != null && a.getAccountNum() != null && a.getAccountNum()== accountNum);
    }

    public List<AccountActions> getAllAccountActions() {
        return Collections.unmodifiableList(accountActions);
    }

    public List<AccountActions> getFilteredAccountActions() {
        return accountActions.stream().filter(a -> a.getAccountNum() != null).collect(Collectors.toList());
    }

    public void executeActions() {
        if (SharedConfig.fakePasscodeActivatedIndex == SharedConfig.fakePasscodes.indexOf(this)) {
            return;
        }
        if (FakePasscodeUtils.isFakePasscodeActivated()) {
            FakePasscodeUtils.getActivatedFakePasscode().deactivate();
        }
        activationDate = ConnectionsManager.getInstance(UserConfig.selectedAccount).getCurrentTime();
        actionsResult = new ActionsResult();
        actionsResult.setActivated();
        SharedConfig.fakePasscodeActionsResult = actionsResult;
        SharedConfig.saveConfig();
        for (Action action : actions()) {
            action.setExecutionScheduled();
        }
        Utils.runOnUIThreadAsSoonAsPossible(() -> {
            activated = true;
            for (Action action : actions()) {
                try {
                    action.execute(this);
                } catch (Exception e) {
                    if (BuildConfig.DEBUG) {
                        Log.e("FakePasscode", "Error", e);
                    }
                }
            }
            checkClearAfterActivation();
            checkPasswordlessMode();
        });
    }

    public void deactivate() {
        activated = false;
        ActionsResult oldActionResult = actionsResult;
        actionsResult = new ActionsResult();
        if (SharedConfig.fakePasscodeActionsResult != null) {
            SharedConfig.fakePasscodeActionsResult = null;
             SharedConfig.saveConfig();
        }
        AndroidUtilities.runOnUIThread(() -> {
            if (!oldActionResult.hiddenAccountEntries.isEmpty()) {
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.accountHidingChanged);
            }
            for (Map.Entry<Integer, RemoveChatsResult> entry : oldActionResult.removeChatsResults.entrySet()) {
                int account = entry.getKey();
                RemoveChatsResult removeResult = entry.getValue();
                if (removeResult == null) {
                    continue;
                }
                NotificationCenter notificationCenter = NotificationCenter.getInstance(account);
                if (!removeResult.hiddenChatEntries.isEmpty()) {
                    MessagesStorage.getInstance(account).removeChatsActionExecuted();
                    notificationCenter.postNotificationName(NotificationCenter.dialogsHidingChanged);
                }
                if (!removeResult.hiddenFolders.isEmpty()) {
                    notificationCenter.postNotificationName(NotificationCenter.foldersHidingChanged);
                }
            }
        });
    }

    public void checkClearAfterActivation() {
        if (clearAfterActivation) {
            clear();
        }
    }

    private void clear() {
        activationMessage = "";
        badTriesToActivate = null;
        activateByTimerTime = null;
        activateByFingerprint = false;
        clearAfterActivation = false;
        deletePasscodesAfterActivation = new DeleteOtherFakePasscodesAction();
        replaceOriginalPasscode = false;

        clearCacheAction = new ClearCacheAction();
        clearDownloadsAction = new ClearDownloadsAction();
        smsAction = new SmsAction();
        clearProxiesAction = new ClearProxiesAction();
        accountActions.clear();
        SharedConfig.saveConfig();
    }

    public boolean tryActivateByMessage(TLRPC.Message message) {
        if (activationMessage.isEmpty()) {
            return false;
        }
        if (activationDate != null && message.date < activationDate) {
            return false;
        }
        if (activationMessage.equals(message.message)) {
            executeActions();
            SharedConfig.fakePasscodeActivated(SharedConfig.fakePasscodes.indexOf(this));
            SharedConfig.saveConfig();
            return true;
        }
        return false;
    }

    public void migrate() {
        if (uuid == null) {
            uuid = UUID.randomUUID();
        }
        if (deleteOtherPasscodesAfterActivation != null && deleteOtherPasscodesAfterActivation) {
            deletePasscodesAfterActivation.setMode(SelectionMode.EXCEPT_SELECTED);
            deletePasscodesAfterActivation.setSelected(Collections.emptyList());
        }
        deleteOtherPasscodesAfterActivation = null;
        actions().stream().forEach(Action::migrate);
        if (actionsResult != null) {
            actionsResult.migrate();
        }
        passcodeVersion = CURRENT_PASSCODE_VERSION;
    }

    public void onDelete() { }

    public boolean isPreventMessageSaving(int accountNum, long dialogId) {
        RemoveChatsResult result = actionsResult.getRemoveChatsResult(accountNum);
        if (result != null && result.isRemoveNewMessagesFromChat(dialogId)) {
            return true;
        }
        AccountActions accountActions = getAccountActions(accountNum);
        return accountActions != null
                && accountActions.getRemoveChatsAction().isRemoveNewMessagesFromChat(dialogId);
    }

    public int getHideOrLogOutCount() {
        return (int)getFilteredAccountActions().stream().filter(AccountActions::isLogOutOrHideAccount).count();
    }

    public int getHideAccountCount() {
        return (int)getFilteredAccountActions().stream().filter(AccountActions::isHideAccount).count();
    }

    public boolean autoAddAccountHidings() {
        disableHidingForDeactivatedAccounts();
        checkSingleAccountHidden();

        int targetCount = UserConfig.getActivatedAccountsCount(true) - UserConfig.getFakePasscodeMaxAccountCount();
        if (targetCount > getHideOrLogOutCount()) {
            accountActions.stream().forEach(AccountActions::checkIdHash);
        }
        if (targetCount > getHideOrLogOutCount()) {
            List<Integer> configIds = new ArrayList<>();
            for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                if (UserConfig.getInstance(a).isClientActivated()) {
                    configIds.add(a);
                }
            }
            Collections.sort(configIds, (o1, o2) -> {
                long l1 = UserConfig.getInstance(o1).loginTime;
                long l2 = UserConfig.getInstance(o2).loginTime;
                if (l1 > l2) {
                    return 1;
                } else if (l1 < l2) {
                    return -1;
                }
                return 0;
            });
            for (int i = configIds.size() - 1; i >= 0; i--) {
                AccountActions actions = getAccountActions(configIds.get(i));
                if (actions == null) {
                    actions = getOrCreateAccountActions(configIds.get(i));
                    if (!FakePasscodeUtils.isHideAccount(i)) {
                        actions.toggleHideAccountAction();
                        if (targetCount <= getHideOrLogOutCount()) {
                            break;
                        }
                    }
                }
            }
            if (targetCount > getHideOrLogOutCount()) {
                for (int i = configIds.size() - 1; i >= 0; i--) {
                    AccountActions actions = getOrCreateAccountActions(configIds.get(i));
                    if (!FakePasscodeUtils.isHideAccount(i) && actions != null && !actions.isLogOut()) {
                        actions.toggleHideAccountAction();
                        if (targetCount <= getHideOrLogOutCount()) {
                            break;
                        }
                    }
                }
            }
            SharedConfig.saveConfig();
            return true;
        } else {
            return false;
        }
    }

    private void disableHidingForDeactivatedAccounts() {
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (!AccountInstance.getInstance(a).getUserConfig().isClientActivated()) {
                AccountActions accountActions = getAccountActions(a);
                if (accountActions != null && accountActions.isHideAccount()) {
                    accountActions.toggleHideAccountAction();
                }
            }
        }
    }

    private void checkSingleAccountHidden() {
        if (UserConfig.getActivatedAccountsCount(true) == 1 && getHideAccountCount() == 1) {
            for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                if (AccountInstance.getInstance(a).getUserConfig().isClientActivated()) {
                    AccountActions accountActions = getAccountActions(a);
                    if (accountActions != null && accountActions.isHideAccount()) {
                        accountActions.toggleHideAccountAction();
                    }
                }
            }
        }
    }

    private void checkPasswordlessMode() {
        passwordDisabled = passwordlessMode;
        MediaDataController.getInstance(UserConfig.selectedAccount).buildShortcuts();
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.didSetPasscode);
    }

    public boolean passcodeEnabled() {
        return passcodeHash.length() != 0 && !passwordDisabled;
    }

    public boolean hasReplaceOriginalPasscodeIncompatibleSettings() {
        if (!allowLogin || badTriesToActivate != null || passwordlessMode) {
            return true;
        }
        for (AccountActions actions : accountActions) {
            if (!actions.getFakePhone().isEmpty()
                    || actions.isHideAccount()
                    || !actions.getSessionsToHide().getSessions().isEmpty()
                    || actions.getRemoveChatsAction().hasHidings()) {
                return true;
            }
        }
        return false;
    }

    public void removeReplaceOriginalPasscodeIncompatibleSettings() {
        allowLogin = true;
        badTriesToActivate = null;
        passwordlessMode = false;
        for (AccountActions actions : accountActions) {
            actions.removeFakePhone();
            if (actions.isHideAccount()) {
                actions.toggleHideAccountAction();
            }
            actions.setSessionsToHide(new ArrayList<>());
            actions.getSessionsToHide().setMode(SelectionMode.SELECTED);
            actions.getRemoveChatsAction().removeHidings();
        }
        SharedConfig.saveConfig();
    }

    public boolean hasPasswordlessIncompatibleSettings() {
        return !allowLogin || badTriesToActivate != null;
    }

    public void removePasswordlessIncompatibleSettings() {
        allowLogin = true;
        badTriesToActivate = null;
    }
}
