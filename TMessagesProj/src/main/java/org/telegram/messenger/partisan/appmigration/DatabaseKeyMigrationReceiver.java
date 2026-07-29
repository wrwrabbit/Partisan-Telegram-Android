package org.telegram.messenger.partisan.appmigration;

import android.os.Build;

import org.telegram.SQLite.SQLiteDatabase;
import org.telegram.messenger.partisan.PartisanLog;
import org.telegram.messenger.partisan.fileprotection.FileProtectionDbEncryption;
import org.telegram.messenger.partisan.fileprotection.FileProtectionEncryptionKeyStore;
import org.telegram.messenger.partisan.fileprotection.FileProtectionEncryptionKeyStore.KeyType;

import java.io.File;
import java.nio.charset.StandardCharsets;

/**
 * Target-side half of the database encryption key migration (see KeyMigrationSender for the source
 * side). Replaces each transferred key with a fresh one wrapped by this app's own
 * keystore, re-encrypting the database.
 */
public class DatabaseKeyMigrationReceiver {

    /**
     * Called before every database open. If a migrated keys file is present, replaces the
     * account's transferred key with a fresh one and re-encrypts the database with it, so
     * that flash remnants of the keys file can't decrypt the database later. Guarantees the
     * database is left in an openable state (deleting it if the data is unrecoverable).
     */
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

    // No key was migrated for this account, but a key blob may have arrived with the migrated
    // preferences. It was wrapped by the source app's keystore and can never be unwrapped here.
    private static void cleanUpForeignKeyBlobIfNeeded(int account, File dbFile, boolean dbEncrypted) throws Exception {
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
