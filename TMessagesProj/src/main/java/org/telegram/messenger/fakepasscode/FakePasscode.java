package org.telegram.messenger.fakepasscode;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.android.exoplayer2.util.Log;
import com.google.common.collect.Lists;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
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
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@JsonIgnoreProperties(ignoreUnknown=true)
public class FakePasscode {
    @JsonIgnore
    private final int CURRENT_PASSCODE_VERSION = 5;
    private int passcodeVersion = CURRENT_PASSCODE_VERSION;

    @JsonProperty(value = "PLATFORM", access = JsonProperty.Access.READ_ONLY)
    public String getPlatform() {
        return "ANDROID";
    }

    public UUID uuid;
    public boolean allowLogin = true;
    public String name;
    @FakePasscodeSerializer.Ignore
    private String passcodeHash = "";
    @FakePasscodeSerializer.Ignore
    private byte[] passcodeSalt;
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
        fakePasscode.name = generatePasscodeName();
        fakePasscode.autoAddAccountHidings();
        return fakePasscode;
    }

    private static String generatePasscodeName() {
        String base = SharedConfig.passcodeType == SharedConfig.PASSCODE_TYPE_PIN
                ? LocaleController.getString(R.string.FakePasscode)
                : LocaleController.getString(R.string.FakePassword);
        return base + " " + (SharedConfig.fakePasscodeIndex);
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
        setDisableFileProtectionAfterRestartByFakePasscodeIfNeed(true);
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
        setDisableFileProtectionAfterRestartByFakePasscodeIfNeed(false);
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
        if (passcodeVersion < 4) {
            if (SharedConfig.fakePasscodeActivatedIndex == SharedConfig.fakePasscodes.indexOf(this)) {
                activated = true;
            }
        }
        if (passcodeVersion < 5) {
            passcodeSalt = SharedConfig.passcodeSalt;
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

    private int getMaxAccountCount() {
        boolean hasPremium = getFilteredAccountActions()
                .stream()
                .filter(a -> !a.isLogOutOrHideAccount())
                .anyMatch(a -> UserConfig.getInstance(a.accountNum).isPremium());
        return hasPremium
                ? UserConfig.FAKE_PASSCODE_MAX_PREMIUM_ACCOUNT_COUNT
                : UserConfig.FAKE_PASSCODE_MAX_ACCOUNT_COUNT;
    }

    private boolean isHidingCountCorrect() {
        int notHiddenCount = UserConfig.getActivatedAccountsCount(true) - getHideOrLogOutCount();
        return notHiddenCount <= getMaxAccountCount();
    }

    public boolean autoAddAccountHidings() {
        disableHidingForDeactivatedAccounts();
        checkSingleAccountHidden();

        if (!isHidingCountCorrect()) {
            accountActions.stream().forEach(AccountActions::checkIdHash);
        }
        if (replaceOriginalPasscode || isHidingCountCorrect()) {
            return false;
        }
        List<Integer> accounts = Utils.getActivatedAccountsSortedByLoginTime();
        for (int account : Lists.reverse(accounts)) {
            AccountActions actions = getOrCreateAccountActions(account);
            if (actions != null && !actions.isLogOut()) {
                actions.toggleHideAccountAction();
                if (isHidingCountCorrect()) {
                    break;
                }
            }
        }
        SharedConfig.saveConfig();
        return true;
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
        if (passwordDisabled) {
            SharedConfig.setAppLocked(false);
            SharedConfig.isWaitingForPasscodeEnter = false;
            SharedConfig.saveConfig();
            MediaDataController.getInstance(UserConfig.selectedAccount).buildShortcuts();
            AndroidUtilities.runOnUIThread(() -> {
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.didSetPasscode);
            });
        }
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

    public void generatePasscodeHash(String password) {
        passcodeSalt = new byte[16];
        Utilities.random.nextBytes(passcodeSalt);
        passcodeHash = calculateHash(password, passcodeSalt);
    }

    private static String calculateHash(String password, byte[] salt) {
        try {
            byte[] passcodeBytes = password.getBytes("UTF-8");
            byte[] bytes = new byte[32 + passcodeBytes.length];
            System.arraycopy(salt, 0, bytes, 0, 16);
            System.arraycopy(passcodeBytes, 0, bytes, 16, passcodeBytes.length);
            System.arraycopy(salt, 0, bytes, passcodeBytes.length + 16, 16);
            return Utilities.bytesToHex(Utilities.computeSHA256(bytes, 0, bytes.length));
        } catch (Exception e) {
            FileLog.e(e);
        }
        return null;
    }

    public boolean validatePasscode(String password) {
        return Objects.equals(calculateHash(password, passcodeSalt), passcodeHash);
    }

    public void replaceOriginalPasscodeIfNeed() {
        if (!replaceOriginalPasscode) {
            return;
        }
        SharedConfig.fakePasscodeActivatedIndex = -1;
        SharedConfig.passcodeSalt = passcodeSalt;
        SharedConfig.setPasscode(passcodeHash);
        SharedConfig.fakePasscodes.remove(this);
    }

    private void setDisableFileProtectionAfterRestartByFakePasscodeIfNeed(boolean disable) {
        if (!SharedConfig.fileProtectionWorksWhenFakePasscodeActivated) {
            Utils.foreachActivatedAccountInstance(accountInstance -> {
                UserConfig userConfig = accountInstance.getUserConfig();
                if (userConfig.disableFileProtectionAfterRestartByFakePasscode != disable
                        && (!disable || accountInstance.getMessagesStorage().fileProtectionEnabled())) {
                    userConfig.disableFileProtectionAfterRestartByFakePasscode = disable;
                    accountInstance.getUserConfig().saveConfig(false);
                }
            });
        }
    }
}
