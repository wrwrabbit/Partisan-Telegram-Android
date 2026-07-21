package org.telegram.messenger.partisan.fileprotection;

import android.content.SharedPreferences;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.SQLite.SQLiteDatabase;
import org.telegram.SQLite.SQLitePreparedStatement;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.partisan.PartisanLog;

import java.nio.ByteBuffer;
import java.util.Map;

public class DraftsLocationReconciler {

    public static void reconcileDraftsLocation(int account, SQLiteDatabase database) {
        try {
            SharedPreferences preferences = DraftsStorage.getPreferences(account);
            if (FileProtectionUtils.encryptionEnabledByConfig(account)) {
                movePreferencesToDatabase(preferences, database);
            } else {
                moveDatabaseToPreferences(database, preferences);
            }
        } catch (Exception e) {
            PartisanLog.e("", e);
        }
    }

    private static void movePreferencesToDatabase(SharedPreferences preferences, SQLiteDatabase database) throws Exception {
        Map<String, ?> entries = preferences.getAll();
        if (entries.isEmpty()) {
            return;
        }
        database.beginTransaction();
        for (Map.Entry<String, ?> entry : entries.entrySet()) {
            if (!(entry.getValue() instanceof String)) {
                continue;
            }
            SQLitePreparedStatement statement = database.executeFast("REPLACE INTO drafts(key, data) VALUES(?, ?)");
            statement.bindString(1, entry.getKey());
            statement.bindByteBuffer(2, ByteBuffer.wrap(Utilities.hexToBytes((String) entry.getValue())));
            statement.step();
            statement.dispose();
        }
        database.commitTransaction();
        // Only clear the source after the DB copy is committed, so a mid-migration failure never loses data.
        preferences.edit().clear().commit();
    }

    private static void moveDatabaseToPreferences(SQLiteDatabase database, SharedPreferences preferences) throws Exception {
        SQLiteCursor cursor = database.queryFinalized("SELECT key, data FROM drafts");
        SharedPreferences.Editor editor = null;
        while (cursor.next()) {
            if (editor == null) {
                editor = preferences.edit();
            }
            editor.putString(cursor.stringValue(0), Utilities.bytesToHex(cursor.byteArrayValue(1)));
        }
        cursor.dispose();
        if (editor != null) {
            editor.commit();
            database.executeFast("DELETE FROM drafts").stepThis().dispose();
        }
    }
}
