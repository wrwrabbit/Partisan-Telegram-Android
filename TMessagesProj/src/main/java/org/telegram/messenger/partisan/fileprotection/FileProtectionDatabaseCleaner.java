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
        public boolean keepEncryptedGroups = true;
        public DialogIdFormat dialogIdFormat = DialogIdFormat.DIALOG_ID;

        public TableInfo(String tableName) {
            this.tableName = tableName;
        }
    }

    private final int account;
    private final SQLiteDatabase db;
    private final Set<Long> recentSearchDialogIds = new HashSet<>();

    public FileProtectionDatabaseCleaner(SQLiteDatabase db, int account) {
        this.db = db;
        this.account = account;
    }

    public void clear() throws Exception {
        try {
            loadRecentSearchDialogIds();
            clearTable(new TableInfo("users") {{ keepRecentSearch = true; }});
            clearTable(new TableInfo("chats") {{ keepRecentSearch = true; }});
            clearTable(new TableInfo("contacts"));
            clearTable(new TableInfo("messages_v2"));
            clearTable(new TableInfo("dialogs") {{ dialogIdColumn = "did"; }});
            clearTable(new TableInfo("messages_holes"));
            clearTable(new TableInfo("messages_topics"));
            clearTable(new TableInfo("messages_holes_topics"));
            clearTable(new TableInfo("media_v4"));
            clearTable(new TableInfo("media_holes_topics"));
            clearTable(new TableInfo("media_holes_v2"));
        } finally {
            AndroidUtilities.runOnUIThread(() -> getNotificationCenter().postNotificationName(NotificationCenter.onFileProtectedDbCleared));
        }
    }

    private void loadRecentSearchDialogIds() throws Exception {
        SQLiteCursor cursor = null;
        try {
            cursor = db.queryFinalized("SELECT did FROM search_recent WHERE 1");
            recentSearchDialogIds.clear();
            while (cursor.next()) {
                recentSearchDialogIds.add(cursor.longValue(0));
            }
        } finally {
            if (cursor != null) {
                cursor.dispose();
            }
            AndroidUtilities.runOnUIThread(() -> getNotificationCenter().postNotificationName(NotificationCenter.onFileProtectedDbCleared));
        }
    }

    private void clearTable(TableInfo tableInfo) throws Exception {
        if (tableInfo.keepRecentSearch) {
            Set<Long> dialogIdsToDelete = loadDialogIdsToDelete(tableInfo);
            for (Long chatId : dialogIdsToDelete) {
                String query = String.format(Locale.US, "DELETE FROM %s WHERE %s = %d", tableInfo.tableName, tableInfo.dialogIdColumn, chatId);
                db.executeFast(query).stepThis().dispose();
            }
        } else {
            String query = String.format(Locale.US, "DELETE FROM %s", tableInfo.tableName);
            if (tableInfo.keepEncryptedGroups) {
                query += " WHERE did & 0x4000000000000000 = 0 OR did & 0x8000000000000000 <> 0".replace("did", tableInfo.dialogIdColumn);
            }
            db.executeFast(query).stepThis().dispose();
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
                if (tableInfo.keepEncryptedGroups && DialogObject.isEncryptedDialog(dialogId)) {
                    continue;
                }
                dialogIdsToDelete.add(dialogId);
            }
            return dialogIdsToDelete;
        } finally {
            if (cursor != null) {
                cursor.dispose();
            }
        }
    }

    private NotificationCenter getNotificationCenter() {
        return NotificationCenter.getInstance(account);
    }
}
