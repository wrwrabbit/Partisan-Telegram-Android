package org.telegram.messenger.fakepasscode;

import android.util.Base64;

import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class AccountActions implements Action {
    @FakePasscodeSerializer.Ignore
    public Integer accountNum = null;
    public RemoveChatsAction removeChatsAction = new RemoveChatsAction();
    public TelegramMessageAction telegramMessageAction = new TelegramMessageAction();
    public DeleteContactsAction deleteContactsAction = null;
    public DeleteStickersAction deleteStickersAction = null;
    public ClearSearchHistoryAction clearSearchHistoryAction = null;
    public ClearBlackListAction clearBlackListAction = null;
    public ClearSavedChannelsAction clearSavedChannelsAction = null;
    public ClearDraftsAction clearDraftsAction = null;
    public TerminateOtherSessionsAction terminateOtherSessionsAction = new TerminateOtherSessionsAction();
    public LogOutAction logOutAction = null;
    public HideAccountAction hideAccountAction = null;
    public String fakePhone = "";
    public CheckedSessions sessionsToHide = new CheckedSessions();
    public String salt = null;
    public String idHash = null;

    public static boolean updateIdHashEnabled = true;

    AccountActions() {
        if (updateIdHashEnabled) {
            Utilities.globalQueue.postRunnable(new UpdateIdHashRunnable(this), 1000);
        }
    }

    @Override
    public void setExecutionScheduled() {
        Arrays.asList(removeChatsAction, telegramMessageAction, deleteContactsAction,
                deleteStickersAction, clearSearchHistoryAction, clearBlackListAction,
                clearSavedChannelsAction, clearDraftsAction, terminateOtherSessionsAction,
                hideAccountAction, logOutAction)
                .stream()
                .filter(Objects::nonNull)
                .forEach(Action::setExecutionScheduled);
    }

    @Override
    public void execute(FakePasscode fakePasscode) {
        if (accountNum == null) {
            return;
        }


        fakePasscode.actionsResult.putFakePhoneNumber(accountNum, fakePhone);
        Arrays.asList(telegramMessageAction, removeChatsAction, deleteContactsAction,
                deleteStickersAction, clearSearchHistoryAction, clearBlackListAction,
                clearSavedChannelsAction, clearDraftsAction, terminateOtherSessionsAction,
                hideAccountAction)
                .stream()
                .filter(Objects::nonNull)
                .forEach(action -> {
                    action.setAccountNum(accountNum);
                    action.execute(fakePasscode);
                });
        if (logOutAction != null) {
            logOutAction.setAccountNum(accountNum);
            new CheckLogOutActionRunnable(logOutAction, fakePasscode).run();
        }
    }

    private byte[] getSalt() {
        if (salt == null) {
            byte[] saltBytes = new byte[16];
            Utilities.random.nextBytes(saltBytes);
            salt = Base64.encodeToString(saltBytes, Base64.DEFAULT);
        }
        return Base64.decode(salt, Base64.DEFAULT);
    }

    private String calculateIdHash(TLRPC.User user) {
        String phoneDigits = user.phone.replaceAll("[^0-9]", "");
        if (phoneDigits.length() < 4) {
            throw new RuntimeException("Can't calculate id hash: invalid phone");
        }
        int phoneId = Integer.parseInt(phoneDigits.substring(phoneDigits.length() - 4));
        long sum = (user.id % 10_000 + phoneId) % 10_000;
        byte[] sumBytes = Long.toString(sum).getBytes();
        byte[] bytes = new byte[32 + sumBytes.length];
        byte[] salt = getSalt();
        System.arraycopy(salt, 0, bytes, 0, 16);
        System.arraycopy(sumBytes, 0, bytes, 16, sumBytes.length);
        System.arraycopy(salt, 0, bytes, sumBytes.length + 16, 16);
        return Utilities.bytesToHex(Utilities.computeSHA256(bytes, 0, bytes.length));
    }

    void checkIdHash() {
        if (accountNum != null) {
            UserConfig userConfig = UserConfig.getInstance(accountNum);
            if (userConfig.isConfigLoaded()) {
                if (userConfig.isClientActivated()) {
                    idHash = calculateIdHash(userConfig.getCurrentUser());
                } else {
                    accountNum = null;
                }
            }
        } else {
            checkAccountNum();
        }
    }

    public void checkAccountNum() {
        if (idHash != null) {
            for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                UserConfig userConfig = UserConfig.getInstance(a);
                if (userConfig.isClientActivated()) {
                    if (idHash.equals(calculateIdHash(userConfig.getCurrentUser()))) {
                        for (FakePasscode fakePasscode : SharedConfig.fakePasscodes) {
                            List<AccountActions> actions = fakePasscode.accountActions;
                            final int aFinal = a;
                            if (actions.contains(this) && actions.stream().noneMatch(action -> action.accountNum != null && action.accountNum == aFinal)) {
                                accountNum = a;
                                break;
                            }
                        }
                        break;
                    }
                }
            }
        }
    }

    public boolean allowChangingAccountHiding(FakePasscode fakePasscode) {
        int hiddenAccountCount = fakePasscode.getHideOrLogOutCount();
        int accountCount = UserConfig.getActivatedAccountsCount();
        if (fakePasscode.replaceOriginalPasscode) {
            return false;
        } else if (isHideAccount()) {
            int visibleAccountCount = accountCount - hiddenAccountCount;
            if (visibleAccountCount < UserConfig.FAKE_PASSCODE_MAX_ACCOUNT_COUNT) {
                return true;
            } else if (visibleAccountCount == UserConfig.FAKE_PASSCODE_MAX_ACCOUNT_COUNT) {
                return UserConfig.getInstance(accountNum).isPremium() || fakePasscode.anyPremiumAccountNotHidden();
            } else {
                return false;
            }
        } else {
            return hiddenAccountCount < accountCount - 1;
        }
    }

    public TelegramMessageAction getTelegramMessageAction() {
        return telegramMessageAction;
    }

    public Integer getAccountNum() {
        return accountNum;
    }

    public RemoveChatsAction getRemoveChatsAction() {
        return removeChatsAction;
    }

    public void setAccountNum(Integer accountNum) {
        this.accountNum = accountNum;
    }

    public String getFakePhone() {
        return fakePhone;
    }

    public CheckedSessions getSessionsToHide() {
        return sessionsToHide;
    }

    public HideAccountAction getHideAccountAction() {
        return hideAccountAction;
    }

    public String getIdHash() {
        return idHash;
    }

    public void setFakePhone(String fakePhone) {
        this.fakePhone = fakePhone;
    }

    public TerminateOtherSessionsAction getTerminateOtherSessionsAction() {
        return terminateOtherSessionsAction;
    }

    @SuppressWarnings("unchecked")
    private <T extends AccountAction> void toggle(Class<T> clazz) {
        Field field = getFieldByActionClass(clazz);
        if (field == null) {
            return;
        }
        try {
            if (field.get(this) != null) {
                field.set(this, null);
                return;
            }
            clazz.getConstructors()[0].getParameterTypes();
            T newValue = Arrays.stream(clazz.getConstructors())
                    .filter(constructor -> constructor.getParameterTypes().length == 0)
                    .findAny()
                    .map(constructor -> {
                        try {
                            return (T) constructor.newInstance();
                        } catch (IllegalAccessException | InstantiationException | InvocationTargetException e) {
                            return null;
                        }
                    })
                    .orElse(null);
            field.set(this, newValue);
        } catch (IllegalAccessException ignore) {

        }
    }

    private <T extends AccountAction> Field getFieldByActionClass(Class<T> clazz) {
        for (Field field : AccountActions.class.getDeclaredFields()) {
            if (field.getType() == clazz) {
                field.setAccessible(true);
                return field;
            }
        }
        return null;
    }

    public void toggleDeleteContactsAction() {
        toggle(DeleteContactsAction.class);
        SharedConfig.saveConfig();
    }

    public void toggleDeleteStickersAction() {
        toggle(DeleteStickersAction.class);
        SharedConfig.saveConfig();
    }

    public void toggleClearSearchHistoryAction() {
        toggle(ClearSearchHistoryAction.class);
        SharedConfig.saveConfig();
    }

    public void toggleClearBlackListAction() {
        toggle(ClearBlackListAction.class);
        SharedConfig.saveConfig();
    }

    public void toggleClearSavedChannelsAction() {
        toggle(ClearSavedChannelsAction.class);
        SharedConfig.saveConfig();
    }

    public void toggleClearDraftsAction() {
        toggle(ClearDraftsAction.class);
        SharedConfig.saveConfig();
    }

    public void toggleLogOutAction() {
        toggle(LogOutAction.class);
        SharedConfig.saveConfig();
    }

    public void toggleHideAccountAction() {
        toggle(HideAccountAction.class);
        SharedConfig.saveConfig();
    }

    public void setSessionsToHide(List<Long> sessions) {
        sessionsToHide.setSessions(sessions);
        SharedConfig.saveConfig();
    }

    public boolean isDeleteContacts() {
        return deleteContactsAction != null;
    }

    public boolean isDeleteStickers() {
        return deleteStickersAction != null;
    }

    public boolean isClearSearchHistory() {
        return clearSearchHistoryAction != null;
    }

    public boolean isClearBlackList() {
        return clearBlackListAction != null;
    }

    public boolean isClearSavedChannels() {
        return clearSavedChannelsAction != null;
    }

    public boolean isClearDraftsAction() {
        return clearDraftsAction != null;
    }

    public boolean isLogOut() {
        return logOutAction != null;
    }

    public boolean isHideAccount() {
        return isHideAccount(false);
    }

    public boolean isHideAccount(boolean strictHiding) {
        if (hideAccountAction == null) {
            return false;
        }

        return !strictHiding || hideAccountAction.strictHiding;
    }

    public boolean isLogOutOrHideAccount() {
        return logOutAction != null || hideAccountAction != null;
    }

    public boolean isPreventStickersBulletin() {
        return deleteStickersAction != null && deleteStickersAction.isPreventBulletin();
    }

    public int getChatsToRemoveCount() {
        return removeChatsAction.getChatEntriesToRemove().size();
    }

    public void removeFakePhone() {
        fakePhone = "";
    }
}
