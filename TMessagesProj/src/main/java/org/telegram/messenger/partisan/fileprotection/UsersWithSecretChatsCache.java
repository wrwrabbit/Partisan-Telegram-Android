package org.telegram.messenger.partisan.fileprotection;

import org.telegram.SQLite.SQLiteDatabase;
import org.telegram.messenger.partisan.PartisanLog;

import java.util.HashMap;
import java.util.Map;

public class UsersWithSecretChatsCache extends AbstractFileProtectionExceptionCache {
    private static final Map<Integer, UsersWithSecretChatsCache> instances = new HashMap<>();

    private UsersWithSecretChatsCache() {

    }

    public static synchronized UsersWithSecretChatsCache getOrCreateInstance(int account, SQLiteDatabase db) {
        if (instances.containsKey(account)) {
            return instances.get(account);
        }

        UsersWithSecretChatsCache instance = new UsersWithSecretChatsCache();
        try {
            instance.load(db);
        } catch (Exception e) {
            PartisanLog.handleException(e);
            return new UsersWithSecretChatsCache();
        }
        instances.put(account, instance);
        return instance;
    }

    @Override
    protected String getLoadSqlQuery() {
        return "SELECT DISTINCT user FROM enc_chats";
    }

    public void add(long dialogId) {
        dialogIds.add(dialogId);
    }
}
