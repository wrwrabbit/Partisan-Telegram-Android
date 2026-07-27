package org.telegram.messenger.fakepasscode.results;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.fakepasscode.AccountIdHashUtils;
import org.telegram.messenger.fakepasscode.Action;
import org.telegram.messenger.fakepasscode.ChatFilter;
import org.telegram.messenger.partisan.serialization.PartisanListDeserializer;
import org.telegram.messenger.partisan.serialization.PartisanCollectionSerializer;
import org.telegram.messenger.partisan.serialization.PartisanSetDeserializer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

public class ActionsResult {

    public Map<Integer, AccountActionsResult> accountResults = new HashMap<>();
    public Map<String, AccountActionsResult> unboundAccountResults = new HashMap<>();
    public String salt;

    @Deprecated
    public Map<Integer, RemoveChatsResult> removeChatsResults = new HashMap<>();
    @Deprecated
    public Map<Integer, TelegramMessageResult> telegramMessageResults = new HashMap<>();
    @Deprecated
    public Map<Integer, String> fakePhoneNumbers = new HashMap<>();
    @Deprecated
    @JsonSerialize(using = PartisanCollectionSerializer.class)
    @JsonDeserialize(using = PartisanSetDeserializer.class, contentAs = Integer.class)
    public Set<Integer> hiddenAccounts = Collections.synchronizedSet(new HashSet<>());
    @Deprecated
    @JsonSerialize(using = PartisanCollectionSerializer.class)
    @JsonDeserialize(using = PartisanListDeserializer.class, contentAs = HideAccountResult.class)
    public List<HideAccountResult> hiddenAccountEntries = Collections.synchronizedList(new ArrayList<>());

    @JsonIgnore
    public Set<Action> actionsPreventsLogoutAction = Collections.synchronizedSet(new HashSet<>());
    @JsonIgnore
    private long activationTime = 0;
    @JsonIgnore
    private boolean migrated = false;

    private AccountActionsResult getAccountResult(int accountNum) {
        return accountResults.get(accountNum);
    }

    private AccountActionsResult getOrCreateAccountResult(int accountNum) {
        return accountResults.computeIfAbsent(accountNum, k -> {
            AccountActionsResult accountResult = new AccountActionsResult();
            accountResult.updateIdHash(accountNum, getOrCreateSalt());
            return accountResult;
        });
    }

    private String getOrCreateSalt() {
        if (salt == null) {
            salt = AccountIdHashUtils.generateSalt();
        }
        return salt;
    }

    private <T> T getFromAccountResult(int accountNum, Function<AccountActionsResult, T> getter) {
        AccountActionsResult accountResult = getAccountResult(accountNum);
        return accountResult != null ? getter.apply(accountResult) : null;
    }

    public RemoveChatsResult getRemoveChatsResult(int accountNum) {
        return getFromAccountResult(accountNum, accountResult -> accountResult.removeChatsResult);
    }

    public RemoveChatsResult getOrCreateRemoveChatsResult(int accountNum) {
        AccountActionsResult accountResult = getOrCreateAccountResult(accountNum);
        if (accountResult.removeChatsResult == null) {
            accountResult.removeChatsResult = new RemoveChatsResult();
        }
        return accountResult.removeChatsResult;
    }

    public TelegramMessageResult getTelegramMessageResult(int accountNum) {
        return getFromAccountResult(accountNum, accountResult -> accountResult.telegramMessageResult);
    }

    public TelegramMessageResult getOrCreateTelegramMessageResult(int accountNum) {
        AccountActionsResult accountResult = getOrCreateAccountResult(accountNum);
        if (accountResult.telegramMessageResult == null) {
            accountResult.telegramMessageResult = new TelegramMessageResult();
        }
        return accountResult.telegramMessageResult;
    }

    public void putFakePhoneNumber(int accountNum, String phoneNumber) {
        getOrCreateAccountResult(accountNum).fakePhoneNumber = phoneNumber;
    }

    public String getFakePhoneNumber(int accountNum) {
        return getFromAccountResult(accountNum, accountResult -> accountResult.fakePhoneNumber);
    }

    public void addHideAccountResult(HideAccountResult hideAccountResult) {
        getOrCreateAccountResult(hideAccountResult.accountNum).hideAccountResult = hideAccountResult;
    }

    public void unhideAccount(int accountNum) {
        AccountActionsResult accountResult = getAccountResult(accountNum);
        if (accountResult != null) {
            accountResult.hideAccountResult = null;
        }
    }

    public boolean isHideAccount(int accountNum, boolean strictHiding) {
        AccountActionsResult accountResult = getAccountResult(accountNum);
        return accountResult != null && accountResult.isHidden(strictHiding);
    }

    public List<ChatFilter> getChatFilters(Optional<Integer> accountNum) {
        List<ChatFilter> result = new ArrayList<>();
        if (accountNum.isPresent()) {
            RemoveChatsResult removeChatsResult = getRemoveChatsResult(accountNum.get());
            if (removeChatsResult != null) {
                result.add(removeChatsResult);
            }
            result.add(new HideEncryptedChatsFromEncryptedGroups(accountNum.get()));
        } else {
            for (AccountActionsResult accountResult : accountResults.values()) {
                if (accountResult.removeChatsResult != null) {
                    result.add(accountResult.removeChatsResult);
                }
            }
            for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                if (AccountInstance.getInstance(a).getUserConfig().isClientActivated()) {
                    result.add(new HideEncryptedChatsFromEncryptedGroups(a));
                }
            }
        }
        return result;
    }

    public void checkIdHashes() {
        if (accountResults.isEmpty() && unboundAccountResults.isEmpty()) {
            return;
        }
        Map<Integer, String> currentHashes = calculateCurrentHashes();
        ReconcileChanges reconcileChanges = new ReconcileChanges();
        reconcileBoundAccountResults(currentHashes, reconcileChanges);
        reattachUnboundAccountResults(currentHashes, reconcileChanges);
        if (reconcileChanges.changed) {
            SharedConfig.saveConfig();
            postReconcileNotifications(reconcileChanges.changedRemoveChatsAccounts, reconcileChanges.hiddenAccountsChanged);
        }
    }

    private void reconcileBoundAccountResults(Map<Integer, String> currentHashes, ReconcileChanges reconcileChanges) {
        for (Map.Entry<Integer, AccountActionsResult> oldEntry : new HashSet<>(accountResults.entrySet())) {
            int oldAccountSlot = oldEntry.getKey();
            AccountActionsResult accountResult = oldEntry.getValue();
            String storedHash = accountResult.idHash;
            if (storedHash == null || storedHash.equals(currentHashes.get(oldAccountSlot))) {
                continue;
            }
            Integer newAccountSlot = findNewAccountSlotByHash(currentHashes, storedHash, oldAccountSlot);
            if (newAccountSlot != null) {
                accountResults.remove(oldAccountSlot);
                accountResults.put(newAccountSlot, accountResult);
                reconcileChanges.update(newAccountSlot, accountResult);
            } else if (currentHashes.containsKey(oldAccountSlot)) {
                accountResults.remove(oldAccountSlot);
                unboundAccountResults.put(storedHash, accountResult);
            } else {
                continue;
            }
            reconcileChanges.update(oldAccountSlot, accountResult);
        }
    }

    private void reattachUnboundAccountResults(Map<Integer, String> currentHashes, ReconcileChanges reconcileChanges) {
        for (Map.Entry<String, AccountActionsResult> unboundEntry : new HashSet<>(unboundAccountResults.entrySet())) {
            String storedHash = unboundEntry.getKey();
            AccountActionsResult accountResult = unboundEntry.getValue();
            Integer newAccountSlot = findNewAccountSlotByHash(currentHashes, storedHash, -1);
            if (newAccountSlot == null) {
                continue;
            }
            unboundAccountResults.remove(storedHash);
            accountResults.put(newAccountSlot, accountResult);
            reconcileChanges.update(newAccountSlot, accountResult);
        }
    }

    private static class ReconcileChanges {
        final Set<Integer> changedRemoveChatsAccounts = new HashSet<>();
        boolean hiddenAccountsChanged;
        boolean changed;

        void update(int accountNum, AccountActionsResult accountResult) {
            if (accountResult.removeChatsResult != null) {
                changedRemoveChatsAccounts.add(accountNum);
            }
            hiddenAccountsChanged |= accountResult.hideAccountResult != null;
            changed = true;
        }
    }

    private Map<Integer, String> calculateCurrentHashes() {
        Map<Integer, String> currentHashes = new HashMap<>();
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            String hash = AccountActionsResult.calculateIdHash(a, getOrCreateSalt());
            if (hash != null) {
                currentHashes.put(a, hash);
            }
        }
        return currentHashes;
    }

    private Integer findNewAccountSlotByHash(Map<Integer, String> currentHashes, String hash, int excludeSlot) {
        for (Map.Entry<Integer, String> entry : currentHashes.entrySet()) {
            int slot = entry.getKey();
            if (slot != excludeSlot && hash.equals(entry.getValue()) && !accountResults.containsKey(slot)) {
                return slot;
            }
        }
        return null;
    }

    private void postReconcileNotifications(Set<Integer> changedRemoveChatsAccounts, boolean hiddenAccountsChanged) {
        AndroidUtilities.runOnUIThread(() -> {
            if (hiddenAccountsChanged) {
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.accountHidingChanged);
            }
            for (int account : changedRemoveChatsAccounts) {
                MessagesStorage.getInstance(account).unreadCounterChangedByFakePasscode();
                NotificationCenter notificationCenter = NotificationCenter.getInstance(account);
                notificationCenter.postNotificationName(NotificationCenter.dialogsHidingChanged);
                notificationCenter.postNotificationName(NotificationCenter.foldersHidingChanged);
            }
        });
    }

    public synchronized void migrate() {
        if (migrated) {
            return;
        }
        migrated = true;
        if (moveDeprecatedFieldsIntoAccountResults()) {
            SharedConfig.saveConfig();
        }
    }

    private boolean moveDeprecatedFieldsIntoAccountResults() {
        boolean changed = !removeChatsResults.isEmpty() || !telegramMessageResults.isEmpty()
                || !fakePhoneNumbers.isEmpty() || !hiddenAccountEntries.isEmpty() || !hiddenAccounts.isEmpty();
        for (Map.Entry<Integer, RemoveChatsResult> entry : removeChatsResults.entrySet()) {
            RemoveChatsResult removeChatsResult = entry.getValue();
            if (removeChatsResult != null) {
                removeChatsResult.migrate();
            }
            getOrCreateAccountResult(entry.getKey()).removeChatsResult = removeChatsResult;
        }
        for (Map.Entry<Integer, TelegramMessageResult> entry : telegramMessageResults.entrySet()) {
            getOrCreateAccountResult(entry.getKey()).telegramMessageResult = entry.getValue();
        }
        for (Map.Entry<Integer, String> entry : fakePhoneNumbers.entrySet()) {
            getOrCreateAccountResult(entry.getKey()).fakePhoneNumber = entry.getValue();
        }
        synchronized (hiddenAccountEntries) {
            for (HideAccountResult entry : hiddenAccountEntries) {
                AccountActionsResult accountResult = getOrCreateAccountResult(entry.accountNum);
                if (accountResult.hideAccountResult == null) {
                    accountResult.hideAccountResult = entry;
                } else {
                    accountResult.hideAccountResult.strictHiding |= entry.strictHiding;
                }
            }
        }
        synchronized (hiddenAccounts) {
            for (int accountNum : hiddenAccounts) {
                AccountActionsResult accountResult = getOrCreateAccountResult(accountNum);
                if (accountResult.hideAccountResult == null) {
                    accountResult.hideAccountResult = new HideAccountResult(accountNum, false);
                }
            }
        }
        if (changed) {
            removeChatsResults.clear();
            telegramMessageResults.clear();
            fakePhoneNumbers.clear();
            hiddenAccountEntries.clear();
            hiddenAccounts.clear();
        }
        return changed;
    }

    public void setActivated() {
        activationTime = System.currentTimeMillis();
    }

    public boolean isJustActivated() {
        if (System.currentTimeMillis() - activationTime < 30 * 1000) {
            return true;
        } else {
            activationTime = 0;
            return false;
        }
    }

    public ActionsResult merge(ActionsResult other) {
        if (other == null) {
            return this;
        }
        ActionsResult newResult = new ActionsResult();
        newResult.migrated = true;
        Set<Integer> accountNums = new HashSet<>(accountResults.keySet());
        accountNums.addAll(other.accountResults.keySet());
        for (int accountNum : accountNums) {
            newResult.accountResults.put(accountNum,
                    AccountActionsResult.merge(accountResults.get(accountNum), other.accountResults.get(accountNum)));
        }
        Set<String> idHashes = new HashSet<>(unboundAccountResults.keySet());
        idHashes.addAll(other.unboundAccountResults.keySet());
        for (String idHash : idHashes) {
            newResult.unboundAccountResults.put(idHash,
                    AccountActionsResult.merge(unboundAccountResults.get(idHash), other.unboundAccountResults.get(idHash)));
        }
        newResult.salt = salt != null ? salt : other.salt;
        return newResult;
    }
}
