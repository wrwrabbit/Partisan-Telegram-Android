package org.telegram.messenger.partisan.fileprotection;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.SQLite.SQLiteDatabase;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.NotificationCenter;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class FileProtectionDatabaseCleaner {
    private enum DialogIdFormat {
        DIALOG_ID,
        CHAT_ID
    }

    private static class TableInfo {
        public final String tableName;
        public String dialogIdColumn = "uid";
        public boolean keepRecentSearch = false;
        public boolean keepUsersWithSecretChats = false;
        public boolean keepEncryptedGroups = true;
        public DialogIdFormat dialogIdFormat = DialogIdFormat.DIALOG_ID;

        public TableInfo(String tableName) {
            this.tableName = tableName;
        }
    }

    private final int account;
    private final SQLiteDatabase db;
    private final Set<String> tablesToClean;
    private final RecentSearchCache recentSearchDialogIds = new RecentSearchCache();
    private UsersWithSecretChatsCache usersWithSecretChats;
    private int deletedCount = 0;

    public FileProtectionDatabaseCleaner(SQLiteDatabase db, int account, Set<String> tablesToClean) {
        this.db = db;
        this.account = account;
        this.tablesToClean = tablesToClean;
    }

    public void clear() throws Exception {
        try {
            deletedCount = 0;
            recentSearchDialogIds.load(db);
            usersWithSecretChats = UsersWithSecretChatsCache.getOrCreateInstance(account, db);
            clearTable(new TableInfo("users") {{ keepRecentSearch = true; keepUsersWithSecretChats = true; }});
            clearTable(new TableInfo("chats") {{ keepRecentSearch = true; dialogIdFormat = DialogIdFormat.CHAT_ID; }});
            clearTable(new TableInfo("contacts"));
            clearTable(new TableInfo("messages_v2"));
            clearTable(new TableInfo("dialogs") {{ dialogIdColumn = "did"; }});
            clearTable(new TableInfo("messages_holes"));
            clearTable(new TableInfo("messages_topics"));
            clearTable(new TableInfo("messages_holes_topics"));
            clearTable(new TableInfo("media_v4") {{ keepUsersWithSecretChats = true; }});
            clearTable(new TableInfo("media_holes_topics"));
            clearTable(new TableInfo("media_holes_v2"));
            clearTable(new TableInfo("enc_tasks_v4"));
            clearTable(new TableInfo("randoms_v2"));
            clearTable(new TableInfo("sharing_locations"));
            clearTable(new TableInfo("unread_push_messages"));
            clearTable(new TableInfo("media_topics"));
            clearTable(new TableInfo("saved_dialogs") {{ dialogIdColumn = "did"; }});
            clearTable(new TableInfo("topics") {{ dialogIdColumn = "did"; }});
            clearTable(new TableInfo("scheduled_messages_v2"));
            clearTable(new TableInfo("chat_pinned_v2"));
            clearTable(new TableInfo("ephemeral_messages") {{ dialogIdColumn = "dialog_id"; }});
            clearTable(new TableInfo("bot_keyboard"));
            clearTable(new TableInfo("bot_keyboard_topics"));
            clearTable(new TableInfo("users_data") {{ keepRecentSearch = true; keepUsersWithSecretChats = true; }});
            clearTable(new TableInfo("quick_replies_messages") {{ keepEncryptedGroups = false; }});
            clearTable(new TableInfo("user_phones_v7") {{ keepEncryptedGroups = false; }});
            clearTable(new TableInfo("tag_message_id") {{ keepEncryptedGroups = false; }});
            clearTable(new TableInfo("user_contacts_v7") {{ keepEncryptedGroups = false; }});
            if (deletedCount > 100) {
                compressDb();
            }
        } finally {
            AndroidUtilities.runOnUIThread(() -> getNotificationCenter().postNotificationName(NotificationCenter.onFileProtectedDbCleared));
        }
    }

    private void clearTable(TableInfo tableInfo) throws Exception {
        if (!tablesToClean.contains(tableInfo.tableName)) {
            return;
        }
        if (tableInfo.keepRecentSearch || tableInfo.keepUsersWithSecretChats) {
            Set<Long> dialogIdsToDelete = loadDialogIdsToDelete(tableInfo);
            for (Long chatId : dialogIdsToDelete) {
                String query = String.format(Locale.US, "DELETE FROM %s WHERE %s = %d", tableInfo.tableName, tableInfo.dialogIdColumn, chatId);
                db.executeFast(query).stepThis().dispose();
            }
            deletedCount += dialogIdsToDelete.size();
        } else {
            String query = String.format(Locale.US, "DELETE FROM %s", tableInfo.tableName);
            if (tableInfo.keepEncryptedGroups) {
                query += " WHERE did & 0x4000000000000000 = 0 OR did & 0x8000000000000000 <> 0".replace("did", tableInfo.dialogIdColumn);
            }
            db.executeFast(query).stepThis().dispose();
            deletedCount += getDbChangesCount();
        }
    }

    private Set<Long> loadDialogIdsToDelete(TableInfo tableInfo) throws Exception {
        SQLiteCursor cursor = null;
        try {
            cursor = db.queryFinalized(String.format(Locale.US, "SELECT %s FROM %s WHERE 1", tableInfo.dialogIdColumn, tableInfo.tableName));
            Set<Long> dialogIdsToDelete = new HashSet<>();
            while (cursor.next()) {
                long dialogId = cursor.longValue(0);
                if (tableInfo.dialogIdFormat == DialogIdFormat.CHAT_ID) {
                    dialogId = -dialogId;
                }
                if (tableInfo.keepRecentSearch && recentSearchDialogIds.contains(dialogId)) {
                    continue;
                }
                if (tableInfo.keepUsersWithSecretChats && usersWithSecretChats.contains(dialogId)) {
                    continue;
                }
                if (tableInfo.keepEncryptedGroups && DialogObject.isEncryptedDialog(dialogId)) {
                    continue;
                }
                dialogIdsToDelete.add(tableInfo.dialogIdFormat != DialogIdFormat.CHAT_ID ? dialogId : -dialogId);
            }
            return dialogIdsToDelete;
        } finally {
            if (cursor != null) {
                cursor.dispose();
            }
        }
    }

    private int getDbChangesCount() throws Exception {
        SQLiteCursor cursor = null;
        try {
            cursor = db.queryFinalized("SELECT changes()");
            if (cursor.next()) {
                return cursor.intValue(0);
            } else {
                return 0;
            }
        } finally {
            if (cursor != null) {
                cursor.dispose();
            }
        }
    }

    private void compressDb() throws Exception {
        db.executeFast("PRAGMA journal_size_limit = 0").stepThis().dispose();
        try {
            db.executeFast("VACUUM").stepThis().dispose();
        } catch (Exception ignore) {
        }
        db.executeFast("PRAGMA journal_size_limit = -1").stepThis().dispose();
    }

    private NotificationCenter getNotificationCenter() {
        return NotificationCenter.getInstance(account);
    }
}
