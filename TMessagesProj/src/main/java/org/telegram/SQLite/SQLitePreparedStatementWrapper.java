package org.telegram.SQLite;

import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.partisan.fileprotection.FileProtectionData;
import org.telegram.messenger.partisan.fileprotection.UsersWithSecretChatsCache;
import org.telegram.tgnet.NativeByteBuffer;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

public class SQLitePreparedStatementWrapper extends SQLitePreparedStatement {
    private final Map<DbSelector, SQLitePreparedStatement> statements;
    private DbSelector dbSelector = DbSelector.BOTH_DB;

    public SQLitePreparedStatementWrapper(Map<DbSelector, SQLitePreparedStatement> statements) {
        this.statements = statements;
    }

    private void setDbSelector(DbSelector dbSelector) {
        this.dbSelector = dbSelector;
    }

    public void setDbSelectorByDialogId(long dialogId) {
        setDbSelectorByDialogId(dialogId, UserConfig.selectedAccount, false, false);
    }

    public void setDbSelectorByDialogId(long dialogId, int account, boolean keepRecentSearch, boolean keepUsersWithEncryptedChats) {
        DbSelector dbSelector = DbSelector.MEMORY_DB;
        if (DialogObject.isEncryptedDialog(dialogId)) {
            dbSelector = DbSelector.BOTH_DB;
        }
        if (dbSelector == DbSelector.MEMORY_DB && keepRecentSearch && FileProtectionData.isAddedRecentSearch(account, dialogId)) {
            dbSelector = DbSelector.BOTH_DB;
        }
        if (dbSelector == DbSelector.MEMORY_DB && keepUsersWithEncryptedChats) {
            SQLiteDatabase database = MessagesStorage.getInstance(account).getDatabase();
            if (UsersWithSecretChatsCache.getOrCreateInstance(account, database).contains(dialogId)) {
                dbSelector = DbSelector.BOTH_DB;
            }
        }
        setDbSelector(dbSelector);
    }

    private interface StatementFunction<R> {
        R apply(SQLitePreparedStatement statement) throws SQLiteException;
    }

    private interface StatementProcedure {
        void apply(SQLitePreparedStatement statement) throws SQLiteException;
    }

    private <R> R executeFunction(StatementFunction<R> function) throws SQLiteException {
        R result = null;
        if (statements.isEmpty()) {
            throw new RuntimeException();
        }
        if (dbSelector == DbSelector.BOTH_DB) {
            Map<DbSelector, R> results = new HashMap<>();
            for (Map.Entry<DbSelector, SQLitePreparedStatement> pair : statements.entrySet()) {
                DbSelector selector = pair.getKey();
                SQLitePreparedStatement statement = pair.getValue();
                results.put(selector, function.apply(statement));
            }
            result = results.containsKey(DbSelector.FILE_DB)
                    ? results.get(DbSelector.FILE_DB)
                    : results.get(DbSelector.MEMORY_DB);
        } else {
            result = function.apply(statements.get(dbSelector));
        }
        return result;
    }

    private void executeProcedure(StatementProcedure function) throws SQLiteException {
        executeFunction(statement -> {
            function.apply(statement);
            return 0;
        });
    }

    @Override
    public long getStatementHandle() {
        try {
            return executeFunction(SQLitePreparedStatement::getStatementHandle);
        } catch (SQLiteException ignore) {
            return 0;
        }
    }

    @Override
    public SQLiteCursor query(Object[] args) throws SQLiteException {
        return executeFunction(statement -> statement.query(args));
    }

    @Override
    public int step() throws SQLiteException {
        return executeFunction(SQLitePreparedStatement::step);
    }

    @Override
    public SQLitePreparedStatement stepThis() throws SQLiteException {
        return executeFunction(SQLitePreparedStatement::stepThis);
    }

    @Override
    public void requery() throws SQLiteException {
        executeProcedure(SQLitePreparedStatement::requery);
    }

    @Override
    public void dispose() {
        try {
            executeProcedure(SQLitePreparedStatement::dispose);
        } catch (SQLiteException ignore) {
        }
    }

    @Override
    void checkFinalized() throws SQLiteException {
        executeProcedure(SQLitePreparedStatement::checkFinalized);
    }

    @Override
    public void finalizeQuery() {
        try {
            executeProcedure(SQLitePreparedStatement::finalizeQuery);
        } catch (SQLiteException ignore) {
        }
    }

    @Override
    public void bindInteger(int index, int value) throws SQLiteException {
        executeProcedure(statement -> statement.bindInteger(index, value));
    }

    @Override
    public void bindDouble(int index, double value) throws SQLiteException {
        executeProcedure(statement -> statement.bindDouble(index, value));
    }

    @Override
    public void bindByteBuffer(int index, ByteBuffer value) throws SQLiteException {
        executeProcedure(statement -> statement.bindByteBuffer(index, value));
    }

    @Override
    public void bindByteBuffer(int index, NativeByteBuffer value) throws SQLiteException {
        executeProcedure(statement -> statement.bindByteBuffer(index, value));
    }

    @Override
    public void bindString(int index, String value) throws SQLiteException {
        executeProcedure(statement -> statement.bindString(index, value));
    }

    @Override
    public void bindLong(int index, long value) throws SQLiteException {
        executeProcedure(statement -> statement.bindLong(index, value));
    }

    @Override
    public void bindNull(int index) throws SQLiteException {
        executeProcedure(statement -> statement.bindNull(index));
    }
}
