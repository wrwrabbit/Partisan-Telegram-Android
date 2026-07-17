package org.telegram.messenger.partisan.appmigration;

import android.os.Build;

import org.telegram.SQLite.SQLiteDatabase;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.partisan.PartisanLog;
import org.telegram.messenger.partisan.Utils;
import org.telegram.messenger.partisan.fileprotection.FileProtectionDbEncryption;
import org.telegram.messenger.partisan.fileprotection.FileProtectionEncryptionKeyStore;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Target-side half of the database encryption key migration (see FileProtectionKeyMigrationSender
 * for the source side). Replaces each transferred key with a fresh one wrapped by this app's own
 * keystore, re-encrypting the database.
 */
public class FileProtectionKeyMigrationReceiver {

    /**
     * Called before every database open. If a migrated keys file is present, replaces the
     * account's transferred key with a fresh one and re-encrypts the database with it, so
     * that flash remnants of the keys file can't decrypt the database later. Guarantees the
     * database is left in an openable state (deleting it if the data is unrecoverable).
     */
    public static synchronized void takeOverMigratedKeyIfNeeded(int account, File dbFile) throws Exception {
        File keysFile = FileProtectionKeyMigrationSender.getKeysFile();
        if (!keysFile.exists()) {
            return;
        }
        try {
            byte[] oldKey = readAndRemoveKey(keysFile, account);
            boolean dbEncrypted = FileProtectionDbEncryption.hasContent(dbFile) && FileProtectionDbEncryption.isEncrypted(dbFile);
            if (oldKey == null) {
                cleanUpForeignKeyBlobIfNeeded(account, dbFile, dbEncrypted);
                return;
            }
            FileProtectionEncryptionKeyStore.deleteKey(account);
            if (!dbEncrypted) {
                return;
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                FileProtectionDbEncryption.decryptDatabase(dbFile, FileProtectionDbEncryption.toKeySpec(oldKey));
                return;
            }
            byte[] newKey = FileProtectionEncryptionKeyStore.getOrCreateKey(account);
            if (newKey == null) {
                throw new IllegalStateException("failed to create a new database key");
            }
            rekeyDatabase(dbFile, FileProtectionDbEncryption.toKeySpec(oldKey), FileProtectionDbEncryption.toKeySpec(newKey));
            PartisanLog.d("FileProtectionKeyMigrationReceiver: the database of account " + account + " was re-encrypted with a new key");
        } catch (Exception e) {
            PartisanLog.e("FileProtectionKeyMigrationReceiver: failed to take over the migrated key, deleting the database", e);
            // never leave a database behind that can't be opened
            FileProtectionEncryptionKeyStore.deleteKey(account);
            FileProtectionDbEncryption.deleteWithSidecars(dbFile);
        }
    }

    // No key was migrated for this account, but a key blob may have arrived with the migrated
    // preferences. It was wrapped by the source app's keystore and can never be unwrapped here.
    private static void cleanUpForeignKeyBlobIfNeeded(int account, File dbFile, boolean dbEncrypted) throws Exception {
        if (FileProtectionEncryptionKeyStore.keyBlobExists(account)
                && FileProtectionEncryptionKeyStore.getKeyIfExists(account) == null) {
            PartisanLog.e("FileProtectionKeyMigrationReceiver: account " + account + " has a foreign key blob but no migrated key");
            FileProtectionEncryptionKeyStore.deleteKey(account);
            if (dbEncrypted) {
                FileProtectionDbEncryption.deleteWithSidecars(dbFile);
            }
        }
    }

    // Removing the entry before using it means a crash can't process the same key twice;
    // if the takeover doesn't complete, the next open falls back to the lost-key cleanup.
    private static byte[] readAndRemoveKey(File keysFile, int account) throws Exception {
        String prefix = account + ":";
        List<String> lines = readLines(keysFile);
        List<String> remaining = new ArrayList<>();
        byte[] key = null;
        boolean removed = false;
        for (String line : lines) {
            if (!removed && line.startsWith(prefix)) {
                key = parseKey(line.substring(prefix.length()));
                removed = true;
            } else {
                remaining.add(line);
            }
        }
        if (removed) {
            if (remaining.isEmpty()) {
                Utils.shredFile(keysFile);
            } else {
                Utils.shredFileContent(keysFile);
                writeLines(keysFile, remaining);
            }
        }
        return key;
    }

    private static byte[] parseKey(String hex) {
        try {
            byte[] key = Utilities.hexToBytes(hex.trim());
            return key != null && key.length == FileProtectionEncryptionKeyStore.DB_KEY_LENGTH ? key : null;
        } catch (Exception e) {
            return null;
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

    private static List<String> readLines(File file) throws Exception {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.US_ASCII))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isEmpty()) {
                    lines.add(line);
                }
            }
        }
        return lines;
    }

    private static void writeLines(File file, List<String> lines) throws Exception {
        StringBuilder builder = new StringBuilder();
        for (String line : lines) {
            builder.append(line).append('\n');
        }
        try (FileOutputStream stream = new FileOutputStream(file)) {
            stream.write(builder.toString().getBytes(StandardCharsets.US_ASCII));
        }
    }
}
