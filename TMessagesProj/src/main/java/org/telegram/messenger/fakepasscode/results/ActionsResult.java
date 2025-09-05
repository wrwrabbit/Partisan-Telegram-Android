package org.telegram.messenger.fakepasscode.results;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.fakepasscode.Action;
import org.telegram.messenger.fakepasscode.ChatFilter;
import org.telegram.messenger.partisan.serialization.PartisanListDeserializer;
import org.telegram.messenger.partisan.serialization.PartisanListSerializer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ActionsResult {

    public Map<Integer, RemoveChatsResult> removeChatsResults = new HashMap<>();
    public Map<Integer, TelegramMessageResult> telegramMessageResults = new HashMap<>();
    public Map<Integer, String> fakePhoneNumbers = new HashMap<>();
    @Deprecated
    public Set<Integer> hiddenAccounts = Collections.synchronizedSet(new HashSet<>());

    @JsonSerialize(using = PartisanListSerializer.class)
    @JsonDeserialize(using = PartisanListDeserializer.class, contentAs = HideAccountResult.class)
    public List<HideAccountResult> hiddenAccountEntries = Collections.synchronizedList(new ArrayList<>());

    @JsonIgnore
    public Set<Action> actionsPreventsLogoutAction = Collections.synchronizedSet(new HashSet<>());
    @JsonIgnore
    private long activationTime = 0;

    public RemoveChatsResult getRemoveChatsResult(int accountNum) {
        return removeChatsResults.get(accountNum);
    }

    public RemoveChatsResult getOrCreateRemoveChatsResult(int accountNum) {
        return putIfAbsent(removeChatsResults, accountNum, new RemoveChatsResult());
    }

    public TelegramMessageResult getTelegramMessageResult(int accountNum) {
        return telegramMessageResults.get(accountNum);
    }

    public TelegramMessageResult getOrCreateTelegramMessageResult(int accountNum) {
        return putIfAbsent(telegramMessageResults, accountNum, new TelegramMessageResult());
    }

    public void putFakePhoneNumber(int accountNum, String phoneNumber) {
        fakePhoneNumbers.put(accountNum, phoneNumber);
    }

    public String getFakePhoneNumber(int accountNum) {
        return fakePhoneNumbers.get(accountNum);
    }

    public boolean isHideAccount(int accountNum, boolean strictHiding) {
        return hiddenAccountEntries.stream().anyMatch(entry -> entry.isHideAccount(accountNum, strictHiding));
    }

    public List<ChatFilter> getChatFilters(Optional<Integer> accountNum) {
        List<ChatFilter> result;
        if (accountNum.isPresent()) {
            result = new ArrayList<>();
            if (removeChatsResults.containsKey(accountNum.get())) {
                result.add(removeChatsResults.get(accountNum.get()));
            }
            result.add(new HideEncryptedChatsFromEncryptedGroups(accountNum.get()));
        } else {
            result = new ArrayList<>(removeChatsResults.values());
            for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                if (AccountInstance.getInstance(a).getUserConfig().isClientActivated()) {
                    result.add(new HideEncryptedChatsFromEncryptedGroups(a));
                }
            }
        }
        return result;
    }

    private static <T> T putIfAbsent(Map<Integer, T> map, int accountNum, T value) {
        T result = map.get(accountNum);
        if (result == null) {
            result = value;
            map.put(accountNum, result);
        }
        return result;
    }

    public void migrate() {
        if (removeChatsResults != null) {
            removeChatsResults.values().stream().forEach(RemoveChatsResult::migrate);
        }
        if (hiddenAccounts != null) {
            hiddenAccountEntries = hiddenAccounts.stream()
                    .map(id -> new HideAccountResult(id, false))
                    .collect(Collectors.toList());
            hiddenAccounts.clear();
        }
        SharedConfig.saveConfig();
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
        newResult.removeChatsResults = mergeMaps(removeChatsResults, other.removeChatsResults);
        newResult.telegramMessageResults = mergeMaps(telegramMessageResults, other.telegramMessageResults);
        newResult.fakePhoneNumbers = mergeMaps(fakePhoneNumbers, other.fakePhoneNumbers);
        newResult.hiddenAccounts = mergeSets(hiddenAccounts, other.hiddenAccounts);
        newResult.hiddenAccountEntries = mergeHiddenAccountEntries(hiddenAccountEntries, other.hiddenAccountEntries);
        return newResult;
    }

    static private <K, V> Map<K, V> mergeMaps(Map<K, V> map1, Map<K, V> map2) {
        Set<K> seen = new HashSet<>();
        return Stream.concat(map1.entrySet().stream(), map2.entrySet().stream())
                .filter(entry -> seen.add(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    static private <T> Set<T> mergeSets(Set<T> set1, Set<T> set2) {
        return Stream.concat(set1.stream(), set2.stream())
                .collect(Collectors.toSet());
    }

    static private List<HideAccountResult> mergeHiddenAccountEntries(List<HideAccountResult> entries1, List<HideAccountResult> entries2) {
        return Stream.concat(entries1.stream(), entries2.stream()).collect(Collectors.toList());
    }
}
