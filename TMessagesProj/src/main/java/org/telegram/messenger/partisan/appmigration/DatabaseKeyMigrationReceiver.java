package org.telegram.messenger.partisan.appmigration;

import android.os.Build;

import androidx.annotation.RequiresApi;

import org.telegram.SQLite.SQLiteDatabase;
import org.telegram.SQLite.SQLiteException;
import org.telegram.messenger.partisan.PartisanLog;
import org.telegram.messenger.partisan.fileprotection.FileProtectionDbEncryption;
import org.telegram.messenger.partisan.fileprotection.FileProtectionEncryptionKeyStore;
import org.telegram.messenger.partisan.fileprotection.FileProtectionEncryptionKeyStore.KeyType;

import java.io.File;
import java.nio.charset.StandardCharsets;

// Target-side half of the database key migration (see KeyMigrationSender for the source side).
public class DatabaseKeyMigrationReceiver {
    // Once set, a blob for this account is ours, not a leftover from the source app's keystore.
    private static final String MIGRATION_TAKEN_OVER = "databaseKeyMigrationTakenOver";
    // SQLite's message for SQLITE_NOTADB, which SQLCipher returns when the key is wrong
    private static final String WRONG_KEY_ERROR = "file is not a database";

    public static synchronized void takeOverMigratedKeyIfNeeded(int account, File dbFile) throws Exception {
        File keysFile = KeyMigrationSender.getKeysFile(KeyType.DATABASE);
        if (!keysFile.exists()) {
            return;
        }
        byte[] migratedKey = MigratedKeyFile.readKey(keysFile, account);
        boolean dbEncrypted = FileProtectionDbEncryption.hasContent(dbFile) && FileProtectionDbEncryption.isEncrypted(dbFile);
        if (migratedKey == null) {
            cleanUpForeignKeyBlobIfNeeded(account, dbFile, dbEncrypted);
            return;
        }
        // The migrated key stays in the file until a stored key opens the database, so a crash or
        // failure anywhere below is retried next launch.
        if (!dbEncrypted) {
            FileProtectionEncryptionKeyStore.deleteKey(KeyType.DATABASE, account);
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            FileProtectionDbEncryption.decryptDatabase(dbFile, FileProtectionDbEncryption.toKeySpec(migratedKey));
            FileProtectionEncryptionKeyStore.deleteKey(KeyType.DATABASE, account);
        } else {
            storeRotatedOrMigratedKey(account, dbFile, migratedKey);
        }
        FileProtectionEncryptionKeyStore.getPreferences(KeyType.DATABASE, account).edit()
                .putBoolean(MIGRATION_TAKEN_OVER, true).apply();
        MigratedKeyFile.readAndRemoveKey(keysFile, account);
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private static void storeRotatedOrMigratedKey(int account, File dbFile, byte[] migratedKey) throws Exception {
        byte[] storedKey = FileProtectionEncryptionKeyStore.getKeyIfExists(KeyType.DATABASE, account);
        if (storedKey != null && opensWithKey(dbFile, storedKey)) {
            // an earlier launch finished the rekey (or the fallback) but not the cleanup
            return;
        }
        if (!opensWithKey(dbFile, migratedKey)) {
            if (storedKey == null && FileProtectionEncryptionKeyStore.keyBlobExists(KeyType.DATABASE, account)) {
                // may be our new key, already rekeyed to but temporarily not unwrappable
                throw new IllegalStateException("the database doesn't open with the migrated key");
            }
            // matches neither key: keep the migrated one and let the open path handle the mismatch
            FileProtectionEncryptionKeyStore.storeKey(KeyType.DATABASE, account, migratedKey);
            return;
        }
        FileProtectionEncryptionKeyStore.deleteKey(KeyType.DATABASE, account);
        byte[] newKey = FileProtectionEncryptionKeyStore.getOrCreateKey(KeyType.DATABASE, account);
        if (newKey != null && tryRekeyDatabase(dbFile, migratedKey, newKey)) {
            PartisanLog.d("DatabaseKeyMigrationReceiver: the database of account " + account + " was re-encrypted with a new key");
            return;
        }
        PartisanLog.e("DatabaseKeyMigrationReceiver: failed to re-encrypt the database of account " + account + ", keeping the migrated key");
        FileProtectionEncryptionKeyStore.storeKey(KeyType.DATABASE, account, migratedKey);
    }

    private static boolean tryRekeyDatabase(File dbFile, byte[] oldKey, byte[] newKey) throws Exception {
        try {
            SQLiteDatabase database = new SQLiteDatabase(dbFile.getPath(), FileProtectionDbEncryption.toKeySpec(oldKey));
            try {
                database.executeInt("PRAGMA user_version"); // verify the old key before rekeying
                String newKeySpec = new String(FileProtectionDbEncryption.toKeySpec(newKey), StandardCharsets.US_ASCII);
                database.executeFast("PRAGMA rekey = \"" + newKeySpec + "\"").stepThis().dispose();
            } finally {
                database.close();
            }
        } catch (Exception e) {
            PartisanLog.e("DatabaseKeyMigrationReceiver: the rekey failed", e);
        }
        return opensWithKey(dbFile, newKey);
    }

    private static boolean opensWithKey(File dbFile, byte[] key) throws Exception {
        SQLiteDatabase database = new SQLiteDatabase(dbFile.getPath(), FileProtectionDbEncryption.toKeySpec(key));
        try {
            database.executeInt("PRAGMA user_version");
            return true;
        } catch (SQLiteException e) {
            if (e.getMessage() != null && e.getMessage().contains(WRONG_KEY_ERROR)) {
                return false;
            }
            throw e;
        } finally {
            database.close();
        }
    }

    // A blob that arrived with the migrated preferences but was never taken over is wrapped by the
    // source app's keystore and can never be unwrapped here.
    private static void cleanUpForeignKeyBlobIfNeeded(int account, File dbFile, boolean dbEncrypted) throws Exception {
        if (FileProtectionEncryptionKeyStore.getPreferences(KeyType.DATABASE, account).getBoolean(MIGRATION_TAKEN_OVER, false)) {
            return;
        }
        if (FileProtectionEncryptionKeyStore.keyBlobExists(KeyType.DATABASE, account)
                && FileProtectionEncryptionKeyStore.getKeyIfExists(KeyType.DATABASE, account) == null) {
            PartisanLog.e("DatabaseKeyMigrationReceiver: account " + account + " has a foreign key blob but no migrated key");
            FileProtectionEncryptionKeyStore.deleteKey(KeyType.DATABASE, account);
            if (dbEncrypted) {
                FileProtectionDbEncryption.deleteWithSidecars(dbFile);
            }
        }
    }
}
