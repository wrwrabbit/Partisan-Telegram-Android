package org.telegram.messenger.partisan;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.SQLite.SQLiteDatabase;
import org.telegram.SQLite.SQLitePreparedStatement;
import org.telegram.messenger.FileLog;

public class PartisanDatabaseMigrationHelper {
    private static final int LAST_PARTISAN_DB_VERSION = 4;

    private final SQLiteDatabase database;
    private int currentVersion = 0;

    public PartisanDatabaseMigrationHelper(SQLiteDatabase database) {
        this.database = database;
    }

    public void updateDb() throws Exception {
        initVersion();
        if (currentVersion == LAST_PARTISAN_DB_VERSION) {
            return;
        }
        PartisanLog.d("PartisanDatabaseMigrationHelper start db migration from " + currentVersion + " to " + LAST_PARTISAN_DB_VERSION);

        migrate();

        if (currentVersion != LAST_PARTISAN_DB_VERSION) {
            throw new Exception("version != LAST_PARTISAN_DB_VERSION");
        }

        FileLog.d("PartisanDatabaseMigrationHelper db migration finished to version " + currentVersion);
    }

    private void initVersion() {
        try {
            currentVersion = 0;
            SQLiteCursor cursor = database.queryFinalized("SELECT version FROM partisan_version");
            if (cursor.next()) {
                currentVersion = cursor.intValue(0);
            }
            cursor.dispose();
        } catch (Exception e) {
            PartisanLog.e("", e);
        }
    }

    private void migrate() throws Exception {
        if (currentVersion == 0) {
            database.executeFast("DROP TABLE IF EXISTS partisan_version").stepThis().dispose();
            database.executeFast("DROP TABLE IF EXISTS enc_groups").stepThis().dispose();
            database.executeFast("DROP TABLE IF EXISTS enc_group_inner_chats").stepThis().dispose();
            database.executeFast("DROP TABLE IF EXISTS enc_group_virtual_messages").stepThis().dispose();
            database.executeFast("DROP TABLE IF EXISTS enc_group_virtual_messages_to_messages_v2").stepThis().dispose();

            database.executeFast("CREATE TABLE partisan_version(version INTEGER)").stepThis().dispose();
            database.executeFast("INSERT INTO partisan_version (version) VALUES (0)").stepThis().dispose();
            database.executeFast("CREATE TABLE enc_groups(encrypted_group_id INTEGER PRIMARY KEY, name TEXT, owner_user_id INTEGER, state TEXT)").stepThis().dispose();
            database.executeFast("CREATE TABLE enc_group_inner_chats(encrypted_group_id INTEGER, encrypted_chat_id INTEGER, user_id INTEGER, state TEXT, " +
                    "PRIMARY KEY(encrypted_group_id, user_id), " +
                    "FOREIGN KEY (encrypted_chat_id) REFERENCES enc_chats(uid) ON DELETE CASCADE)").stepThis().dispose();
            database.executeFast("CREATE INDEX IF NOT EXISTS enc_group_inner_chats_idx ON enc_group_inner_chats(encrypted_chat_id);").stepThis().dispose();

            database.executeFast("CREATE TABLE enc_group_virtual_messages(encrypted_group_id INTEGER, virtual_message_id INTEGER, " +
                    "PRIMARY KEY(encrypted_group_id, virtual_message_id), " +
                    "FOREIGN KEY (encrypted_group_id) REFERENCES enc_groups(encrypted_group_id) ON DELETE CASCADE)").stepThis().dispose();
            database.executeFast("CREATE TABLE enc_group_virtual_messages_to_messages_v2(encrypted_group_id INTEGER, virtual_message_id INTEGER, encrypted_chat_id INTEGER, real_message_id INTEGER, " +
                    "PRIMARY KEY(encrypted_group_id, virtual_message_id, encrypted_chat_id), " +
                    "FOREIGN KEY (encrypted_group_id, virtual_message_id) REFERENCES enc_group_virtual_messages(encrypted_group_id, virtual_message_id) ON DELETE CASCADE)").stepThis().dispose();
            database.executeFast("CREATE INDEX IF NOT EXISTS enc_group_virtual_messages_to_messages_v2_idx ON enc_group_virtual_messages_to_messages_v2(encrypted_group_id, encrypted_chat_id, real_message_id);").stepThis().dispose();

            increaseVersion();
        }

        if (currentVersion == 1) {
            database.executeFast("ALTER TABLE enc_groups ADD COLUMN external_group_id INTEGER").stepThis().dispose();
            database.executeFast("CREATE UNIQUE INDEX IF NOT EXISTS enc_group_external_id_idx ON enc_groups(external_group_id);").stepThis().dispose();

            increaseVersion();
        }

        if (currentVersion == 2) {
            database.executeFast("CREATE INDEX IF NOT EXISTS enc_group_inner_chats_encrypted_chat_id_idx ON enc_group_inner_chats(encrypted_chat_id);").stepThis().dispose();

            increaseVersion();
        }

        if (currentVersion == 3) {
            database.executeFast("DROP INDEX IF EXISTS enc_group_virtual_messages_to_messages_v2_idx").stepThis().dispose();
            database.executeFast("DROP TABLE IF EXISTS enc_group_virtual_messages_to_messages_v2").stepThis().dispose();
            database.executeFast("DROP TABLE IF EXISTS enc_group_virtual_messages").stepThis().dispose();

            increaseVersion();
        }
    }

    private void increaseVersion() throws Exception {
        currentVersion++;
        SQLitePreparedStatement state = database.executeFast("UPDATE partisan_version SET version = ?");
        state.bindInteger(1, currentVersion);
        state.step();
        state.dispose();
    }
}
