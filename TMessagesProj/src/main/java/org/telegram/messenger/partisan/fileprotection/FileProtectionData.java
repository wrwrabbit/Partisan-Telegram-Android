package org.telegram.messenger.partisan.fileprotection;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class FileProtectionData {
    private static final Map<Integer, Set<Long>> addedRecentSearch = new ConcurrentHashMap<>();

    public static void addAddedRecentSearch(int account, long dialogId) {
        Set<Long> dialogIds = addedRecentSearch.computeIfAbsent(account, acc -> Collections.synchronizedSet(new HashSet<>()));
        dialogIds.add(dialogId);
    }

    public static boolean isAddedRecentSearch(int account, long dialogId) {
        if (!addedRecentSearch.containsKey(account)) {
            return false;
        }
        Set<Long> dialogIds = addedRecentSearch.get(account);
        return dialogIds.contains(dialogId);
    }
}
