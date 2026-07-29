package org.telegram.messenger.partisan.appmigration;

import android.os.Build;

import org.telegram.SQLite.SQLiteDatabase;
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

    public static synchronized void takeOverMigratedKeyIfNeeded(int account, File dbFile) throws Exception {
        File keysFile = KeyMigrationSender.getKeysFile(KeyType.DATABASE);
        if (!keysFile.exists()) {
            return;
        }
        try {
            byte[] oldKey = MigratedKeyFile.readAndRemoveKey(keysFile, account);
            boolean dbEncrypted = FileProtectionDbEncryption.hasContent(dbFile) && FileProtectionDbEncryption.isEncrypted(dbFile);
            if (oldKey == null) {
                cleanUpForeignKeyBlobIfNeeded(account, dbFile, dbEncrypted);
                return;
            }
            FileProtectionEncryptionKeyStore.deleteKey(KeyType.DATABASE, account);
            FileProtectionEncryptionKeyStore.getPreferences(KeyType.DATABASE, account).edit()
                    .putBoolean(MIGRATION_TAKEN_OVER, true).apply();
            if (!dbEncrypted) {
                return;
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                FileProtectionDbEncryption.decryptDatabase(dbFile, FileProtectionDbEncryption.toKeySpec(oldKey));
                return;
            }
            byte[] newKey = FileProtectionEncryptionKeyStore.getOrCreateKey(KeyType.DATABASE, account);
            if (newKey == null) {
                throw new IllegalStateException("failed to create a new database key");
            }
            rekeyDatabase(dbFile, FileProtectionDbEncryption.toKeySpec(oldKey), FileProtectionDbEncryption.toKeySpec(newKey));
            PartisanLog.d("DatabaseKeyMigrationReceiver: the database of account " + account + " was re-encrypted with a new key");
        } catch (Exception e) {
            PartisanLog.e("DatabaseKeyMigrationReceiver: failed to take over the migrated key, deleting the database", e);
            // never leave a database behind that can't be opened
            FileProtectionEncryptionKeyStore.deleteKey(KeyType.DATABASE, account);
            FileProtectionDbEncryption.deleteWithSidecars(dbFile);
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

    private static void rekeyDatabase(File dbFile, byte[] oldKeySpec, byte[] newKeySpec) throws Exception {
        SQLiteDatabase database = new SQLiteDatabase(dbFile.getPath(), oldKeySpec);
        try {
            database.executeInt("PRAGMA user_version"); // verify the old key before rekeying
            database.executeFast("PRAGMA rekey = \"" + new String(newKeySpec, StandardCharsets.US_ASCII) + "\"").stepThis().dispose();
        } finally {
            database.close();
        }
    }
}
