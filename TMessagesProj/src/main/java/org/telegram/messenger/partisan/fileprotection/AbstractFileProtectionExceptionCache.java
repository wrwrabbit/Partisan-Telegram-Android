package org.telegram.messenger.partisan.fileprotection;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.SQLite.SQLiteDatabase;

import java.util.HashSet;
import java.util.Set;

public abstract class AbstractFileProtectionExceptionCache {
    protected final Set<Long> dialogIds = new HashSet<>();

    public void load(SQLiteDatabase db) throws Exception {
        SQLiteCursor cursor = null;
        try {
            cursor = db.queryFinalized(getLoadSqlQuery());
            dialogIds.clear();
            while (cursor.next()) {
                dialogIds.add(cursor.longValue(0));
            }
        } finally {
            if (cursor != null) {
                cursor.dispose();
            }
        }
    }

    protected abstract String getLoadSqlQuery();

    public boolean contains(long dialogId) {
        return dialogIds.contains(dialogId);
    }
}
