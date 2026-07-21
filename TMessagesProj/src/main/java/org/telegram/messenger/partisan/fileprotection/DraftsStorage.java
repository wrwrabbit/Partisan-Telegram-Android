package org.telegram.messenger.partisan.fileprotection;

import android.app.Activity;
import android.content.SharedPreferences;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.SQLite.SQLiteException;
import org.telegram.SQLite.SQLitePreparedStatement;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.partisan.AccountControllersProvider;
import org.telegram.tgnet.NativeByteBuffer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class DraftsStorage implements AccountControllersProvider {

    private final int account;
    private final boolean databaseBacked;
    private final SharedPreferences preferences;

    public DraftsStorage(int account) {
        this.account = account;
        this.databaseBacked = FileProtectionUtils.encryptionEnabledByConfig(account);
        this.preferences = getPreferences(account);
    }

    static SharedPreferences getPreferences(int account) {
        return ApplicationLoader.applicationContext.getSharedPreferences(account == 0 ? "drafts" : "drafts" + account, Activity.MODE_PRIVATE);
    }

    // Prefs-backed: callback runs synchronously, same thread.
    // DB-backed: reads on the storage queue, then hops to the UI thread.
    public void getAllAsync(Utilities.Callback<Map<String, String>> callback) {
        if (!databaseBacked) {
            callback.run(readAllFromPreferences());
        } else {
            getMessagesStorage().getStorageQueue().postRunnable(() -> {
                Map<String, String> result = readAllFromDatabase();
                AndroidUtilities.runOnUIThread(() -> callback.run(result));
            });
        }
    }

    private Map<String, String> readAllFromPreferences() {
        Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, ?> entry : preferences.getAll().entrySet()) {
            if (entry.getValue() instanceof String) {
                result.put(entry.getKey(), (String) entry.getValue());
            }
        }
        return result;
    }

    private Map<String, String> readAllFromDatabase() {
        Map<String, String> result = new HashMap<>();
        try {
            SQLiteCursor cursor = getMessagesStorage().getDatabase().queryFinalized("SELECT key, data FROM drafts");
            while (cursor.next()) {
                result.put(cursor.stringValue(0), Utilities.bytesToHex(cursor.byteArrayValue(1)));
            }
            cursor.dispose();
        } catch (SQLiteException e) {
            FileLog.e(e);
        }
        return result;
    }

    public Editor edit() {
        return databaseBacked ? new DatabaseEditor(account) : new PreferencesEditor(preferences.edit());
    }

    @Override
    public int getAccountNum() {
        return account;
    }

    public interface Editor {
        Editor putString(String key, String hexValue);
        Editor remove(String key);
        Editor clear();
        void commit();
        void apply();
    }

    private static class PreferencesEditor implements Editor {
        private final SharedPreferences.Editor editor;

        private PreferencesEditor(SharedPreferences.Editor editor) {
            this.editor = editor;
        }

        @Override
        public Editor putString(String key, String hexValue) {
            editor.putString(key, hexValue);
            return this;
        }

        @Override
        public Editor remove(String key) {
            editor.remove(key);
            return this;
        }

        @Override
        public Editor clear() {
            editor.clear();
            return this;
        }

        @Override
        public void commit() {
            editor.commit();
        }

        @Override
        public void apply() {
            editor.apply();
        }
    }

    private static class DatabaseEditor implements Editor, AccountControllersProvider {
        private final int account;
        private final ArrayList<Runnable> operations = new ArrayList<>();
        private boolean clearAll;

        private DatabaseEditor(int account) {
            this.account = account;
        }

        @Override
        public int getAccountNum() {
            return account;
        }

        @Override
        public Editor putString(String key, String hexValue) {
            operations.add(() -> replaceRow(key, hexValue));
            return this;
        }

        @Override
        public Editor remove(String key) {
            operations.add(() -> removeRow(key));
            return this;
        }

        @Override
        public Editor clear() {
            clearAll = true;
            return this;
        }

        @Override
        public void commit() {
            apply();
        }

        @Override
        public void apply() {
            getMessagesStorage().getStorageQueue().postRunnable(() -> {
                try {
                    if (clearAll) {
                        getMessagesStorage().getDatabase().executeFast("DELETE FROM drafts").stepThis().dispose();
                    }
                    for (Runnable operation : operations) {
                        operation.run();
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            });
        }

        private void replaceRow(String key, String hexValue) {
            try {
                byte[] data = Utilities.hexToBytes(hexValue);
                NativeByteBuffer buffer = new NativeByteBuffer(data.length);
                buffer.writeBytes(data);
                SQLitePreparedStatement statement = getMessagesStorage().getDatabase().executeFast("REPLACE INTO drafts(key, data) VALUES(?, ?)");
                statement.bindString(1, key);
                statement.bindByteBuffer(2, buffer);
                statement.step();
                statement.dispose();
            } catch (Exception e) {
                FileLog.e(e);
            }
        }

        private void removeRow(String key) {
            try {
                SQLitePreparedStatement statement = getMessagesStorage().getDatabase().executeFast("DELETE FROM drafts WHERE key = ?");
                statement.bindString(1, key);
                statement.step();
                statement.dispose();
            } catch (SQLiteException e) {
                FileLog.e(e);
            }
        }
    }
}
