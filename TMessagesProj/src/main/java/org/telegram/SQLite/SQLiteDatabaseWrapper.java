package org.telegram.SQLite;

import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.partisan.PartisanLog;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class SQLiteDatabaseWrapper extends SQLiteDatabase {
    private final Set<String> sqlPrefixesForBothDB = new HashSet<>(Arrays.asList(
            "PRAGMA", "CREATE TABLE", "CREATE INDEX", "VACUUM", "DROP TABLE", "DELETE FROM", "UPDATE", "CREATE INDEX",
            "CREATE UNIQUE INDEX", "ALTER TABLE"
    ));
    private final Set<String> sqlPrefixesForSpecificDB = new HashSet<>(Arrays.asList(
            "INSERT INTO", "REPLACE INTO", "SELECT"
    ));
    private final Set<String> onlyMemoryTables = new HashSet<>(Arrays.asList(
            "messages_v2", "chats", "contacts", "dialogs", "messages_holes", "messages_topics", "messages_holes_topics"
    ));

    private final SQLiteDatabase fileDatabase;
    private final SQLiteDatabase memoryDatabase;

    public SQLiteDatabaseWrapper(String fileName) throws SQLiteException {
        super(fileName);
        fileDatabase = new SQLiteDatabase(fileName);
        memoryDatabase = new SQLiteDatabase(":memory:");
        fileDatabase.backup(memoryDatabase); // copy file database to memory
    }

    public SQLiteDatabase getFileDatabase() {
        return fileDatabase;
    }

    @Override
    public long getSQLiteHandle() {
        return fileDatabase.getSQLiteHandle();
    }

    @Override
    public boolean tableExists(String tableName) throws SQLiteException {
        return fileDatabase.tableExists(tableName);
    }

    private DbSelector getDbSelectorBySqlQuery(String sql) {
        if (sqlPrefixesForBothDB.stream().anyMatch(sql::startsWith)) {
            return DbSelector.BOTH_DB;
        } else if (sqlPrefixesForSpecificDB.stream().anyMatch(sql::startsWith)) {
            if (sql.startsWith("SELECT")) {
                return DbSelector.MEMORY_DB;
            } else {
                String prefix = sqlPrefixesForSpecificDB.stream().filter(sql::startsWith).findFirst().orElse("");
                if (onlyMemoryTables.stream().anyMatch(table -> sql.startsWith(prefix + " " + table))) {
                    return DbSelector.MEMORY_DB;
                } else {
                    return DbSelector.BOTH_DB;
                }
            }
        } else {
            if (PartisanLog.logsAllowed()) {
                PartisanLog.e("Failed execute sql: " + sql);
                AndroidUtilities.runOnUIThread(() ->
                        Toast.makeText(ApplicationLoader.applicationContext, "Wrapped DB error", Toast.LENGTH_SHORT).show()
                );
                throw new RuntimeException();
            }
            return DbSelector.BOTH_DB;
        }
    }

    private interface DbFunction<R> {
        R apply(SQLiteDatabase db) throws SQLiteException;
    }

    private interface DbProcedure {
        void apply(SQLiteDatabase db) throws SQLiteException;
    }

    private <R> Map<DbSelector, R> executeFunctionInSpecificDB(String sql, DbFunction<R> function) throws SQLiteException {
        return executeFunctionInSpecificDB(getDbSelectorBySqlQuery(sql), function);
    }

    private <R> Map<DbSelector, R> executeFunctionInSpecificDB(DbSelector dbSelector, DbFunction<R> function) throws SQLiteException {
        switch (dbSelector) {
            case FILE_DB:
                return Map.of(dbSelector, function.apply(fileDatabase));
            case MEMORY_DB:
                return Map.of(dbSelector, function.apply(memoryDatabase));
            case BOTH_DB:
            default:
                R memoryDbResult;
                R fileDbResult;
                try {
                    memoryDbResult = function.apply(memoryDatabase);
                } catch (Exception e) {
                    PartisanLog.e("Memory database error", e);
                    throw e;
                }
                fileDbResult = function.apply(fileDatabase);
                return Map.of(
                        DbSelector.MEMORY_DB, memoryDbResult,
                        DbSelector.FILE_DB, fileDbResult
                );
        }
    }

    private void executeProcedureInSpecificDB(String sql, DbProcedure procedure) throws SQLiteException {
        executeFunctionInSpecificDB(sql, db -> {
            procedure.apply(db);
            return 0;
        });
    }

    @Override
    public SQLitePreparedStatement executeFast(String sql) throws SQLiteException {
        return new SQLitePreparedStatementWrapper(executeFunctionInSpecificDB(sql, db -> db.executeFast(sql)));
    }

    public SQLitePreparedStatementWrapper executeFastForBothDb(String sql) throws SQLiteException {
        return new SQLitePreparedStatementWrapper(executeFunctionInSpecificDB(DbSelector.BOTH_DB, db -> db.executeFast(sql)));
    }

    @Override
    public Integer executeInt(String sql, Object... args) throws SQLiteException {
        Map<DbSelector, Integer> ints = executeFunctionInSpecificDB(sql, db -> db.executeInt(sql, args));
        return ints.containsKey(DbSelector.FILE_DB)
                ? ints.get(DbSelector.FILE_DB)
                : ints.get(DbSelector.MEMORY_DB);
    }

    @Override
    public void explainQuery(String sql, Object... args) throws SQLiteException {
        executeProcedureInSpecificDB(sql, db -> db.explainQuery(sql, args));
    }

    @Override
    public SQLiteCursor queryFinalized(String sql, Object... args) throws SQLiteException {
        Map<DbSelector, SQLiteCursor> cursors = executeFunctionInSpecificDB(sql, db -> db.queryFinalized(sql, args));
        return cursors.containsKey(DbSelector.MEMORY_DB)
                ? cursors.get(DbSelector.MEMORY_DB)
                : cursors.get(DbSelector.FILE_DB);
    }

    @Override
    public void close() {
        fileDatabase.close();
        memoryDatabase.close();
    }

    @Override
    public void finalize() throws Throwable {
        super.finalize();
        close();
    }

    @Override
    public void beginTransaction() throws SQLiteException {
        fileDatabase.beginTransaction();
        memoryDatabase.beginTransaction();
    }

    @Override
    public void commitTransaction() {
        fileDatabase.commitTransaction();
        memoryDatabase.commitTransaction();
    }
}
