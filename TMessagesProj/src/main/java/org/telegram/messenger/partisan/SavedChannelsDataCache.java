package org.telegram.messenger.partisan;

import android.util.LongSparseArray;
import android.util.SparseArray;

import java.util.ArrayList;

import org.telegram.messenger.MessageObject;

public class SavedChannelsDataCache {
    private static final SparseArray<SavedChannelsDataCache> instances = new SparseArray<>();

    public final LongSparseArray<ArrayList<MessageObject>> messageMap = new LongSparseArray<>();
    public long lastRefreshTime;

    public static SavedChannelsDataCache getInstance(int account) {
        SavedChannelsDataCache cache = instances.get(account);
        if (cache == null) {
            cache = new SavedChannelsDataCache();
            instances.put(account, cache);
        }
        return cache;
    }
}
