package org.telegram.messenger.partisan.fileprotection;

import android.os.Build;

import org.telegram.SQLite.SQLiteDatabase;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.partisan.PartisanLog;
import org.telegram.messenger.partisan.Utils;
import org.telegram.messenger.partisan.appmigration.DatabaseKeyMigrationReceiver;
import org.telegram.messenger.partisan.fileprotection.FileProtectionEncryptionKeyStore.KeyType;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Decides whether an account's database must be opened with a SQLCipher key and performs the
 * one-time plaintext -> encrypted conversion. Only active when the native library was built
 * with SQLCipher (see jni/sqlcipher/README.md); otherwise everything degrades to plain SQLite.
 */
public class FileProtectionDbEncryption {
    // the first 16 bytes of every plaintext SQLite file: "SQLite format 3" plus a trailing NUL
    private static final byte[] SQLITE_FILE_HEADER = Arrays.copyOf("SQLite format 3".getBytes(StandardCharsets.US_ASCII), 16);

    public static byte[] syncDatabaseEncryptionAndGetKeySpec(int account, File dbFile) {
        try {
            restoreBackupIfNeeded(dbFile);
            DatabaseKeyMigrationReceiver.takeOverMigratedKeyIfNeeded(account, dbFile);
            if (!encryptionSupported()) {
                return null;
            }
            if (hasContent(dbFile) && isEncrypted(dbFile)) {
                byte[] keySpec = syncExistingEncryptedFile(account, dbFile);
                if (keySpec != null || hasContent(dbFile)) {
                    // either resolved to a usable key spec, or the file is still encrypted
                    // (just decrypted to plaintext, or the key is temporarily unavailable)
                    return keySpec;
                }
                // the undecryptable database was deleted; fall through to create a fresh one
            }
            return createKeyAndEncryptFileIfEnabled(account, dbFile);
        } catch (Exception e) {
            PartisanLog.e("FileProtectionDbEncryption: failed to prepare the database key", e);
            return null;
        }
    }

    private static byte[] syncExistingEncryptedFile(int account, File dbFile) throws Exception {
        if (!FileProtectionUtils.encryptionEnabledByConfig(account)) {
            decryptOrDeleteEncryptedFile(account, dbFile);
            return null;
        }
        return resolveKeySpecOrDeleteEncryptedFile(account, dbFile);
    }

    private static byte[] createKeyAndEncryptFileIfEnabled(int account, File dbFile) throws Exception {
        if (!FileProtectionUtils.encryptionEnabledByConfig(account)) {
            return null;
        }
        byte[] key = FileProtectionEncryptionKeyStore.getOrCreateKey(KeyType.DATABASE, account);
        if (key == null) {
            return null;
        }
        byte[] keySpec = toKeySpec(key);
        if (hasContent(dbFile)) {
            encryptDatabase(dbFile, keySpec);
        }
        return keySpec;
    }

    // Returns a " KEY ..." clause for "ATTACH DATABASE"
    public static String getAttachKeyClauseIfNeeded(int account, File dbFile) {
        if (!encryptionSupported() || !dbFile.exists() || dbFile.length() == 0) {
            return "";
        }
        try {
            if (!isEncrypted(dbFile)) {
                return "";
            }
        } catch (Exception e) {
            return "";
        }
        byte[] key = FileProtectionEncryptionKeyStore.getKeyIfExists(KeyType.DATABASE, account);
        if (key == null) {
            return "";
        }
        return " KEY \"x'" + Utilities.bytesToHex(key) + "'\"";
    }

    static boolean encryptionSupported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M;
    }

    private static byte[] resolveKeySpecOrDeleteEncryptedFile(int account, File dbFile) throws Exception {
        byte[] key = FileProtectionEncryptionKeyStore.getKeyIfExists(KeyType.DATABASE, account);
        if (key != null) {
            byte[] keySpec = toKeySpec(key);
            if (canOpenWithKey(dbFile, keySpec)) {
                return keySpec;
            } else {
                PartisanLog.e("FileProtectionDbEncryption: the stored key doesn't match the encrypted database, deleting the database");
                deleteWithSidecars(dbFile);
            }
        } else if (!FileProtectionEncryptionKeyStore.keyBlobExists(KeyType.DATABASE, account)) {
            PartisanLog.e("FileProtectionDbEncryption: the database is encrypted but the key is lost, deleting the database");
            deleteWithSidecars(dbFile);
        }
        return null;
    }

    // Called when the file is encrypted but the "Encrypt database" toggle is now off: convert
    // it back to plaintext so it opens without a key. Leaves the database in an openable state.
    private static void decryptOrDeleteEncryptedFile(int account, File dbFile) throws Exception {
        byte[] key = FileProtectionEncryptionKeyStore.getKeyIfExists(KeyType.DATABASE, account);
        if (key != null) {
            byte[] keySpec = toKeySpec(key);
            if (canOpenWithKey(dbFile, keySpec)) {
                decryptDatabase(dbFile, keySpec);
                FileProtectionEncryptionKeyStore.deleteKey(KeyType.DATABASE, account);
            } else {
                PartisanLog.e("FileProtectionDbEncryption: cannot decrypt (stored key doesn't match), deleting the database");
                deleteWithSidecars(dbFile);
                FileProtectionEncryptionKeyStore.deleteKey(KeyType.DATABASE, account);
            }
        } else if (!FileProtectionEncryptionKeyStore.keyBlobExists(KeyType.DATABASE, account)) {
            PartisanLog.e("FileProtectionDbEncryption: cannot decrypt (key lost), deleting the database");
            deleteWithSidecars(dbFile);
        }
        // else: the key is temporarily unavailable -> leave the file encrypted and retry next launch
    }

    public static byte[] toKeySpec(byte[] rawKey) {
        return ("x'" + Utilities.bytesToHex(rawKey) + "'").getBytes(StandardCharsets.US_ASCII);
    }

    private static boolean canOpenWithKey(File dbFile, byte[] keySpec) {
        SQLiteDatabase database;
        try {
            database = new SQLiteDatabase(dbFile.getPath(), keySpec);
            database.executeInt("PRAGMA user_version");
        } catch (Exception e) {
            PartisanLog.e("FileProtectionDbEncryption: failed to open the encrypted database with the stored key", e);
            return false;
        }
        try {
            database.close();
        } catch (Exception e) {
            PartisanLog.e("FileProtectionDbEncryption: failed to close the database after a successful key check", e);
        }
        return true;
    }

    public static boolean hasContent(File dbFile) {
        return dbFile.exists() && dbFile.length() > 0;
    }

    public static boolean isEncrypted(File dbFile) throws Exception {
        byte[] header = new byte[SQLITE_FILE_HEADER.length];
        try (FileInputStream stream = new FileInputStream(dbFile)) {
            if (stream.read(header) < header.length) {
                return false;
            }
        }
        return !Arrays.equals(header, SQLITE_FILE_HEADER);
    }

    private static void encryptDatabase(File dbFile, byte[] keySpec) throws Exception {
        PartisanLog.d("FileProtectionDbEncryption: encrypting the database " + dbFile.getName());
        exportDatabase(dbFile, null, "\"" + new String(keySpec, StandardCharsets.US_ASCII) + "\"");
        PartisanLog.d("FileProtectionDbEncryption: the database was encrypted");
    }

    public static void decryptDatabase(File dbFile, byte[] keySpec) throws Exception {
        PartisanLog.d("FileProtectionDbEncryption: decrypting the database " + dbFile.getName());
        exportDatabase(dbFile, keySpec, "''");
        PartisanLog.d("FileProtectionDbEncryption: the database was decrypted");
    }

    private static void exportDatabase(File dbFile, byte[] openKeySpec, String attachKeyLiteral) throws Exception {
        File exportedFile = new File(dbFile.getParentFile(), dbFile.getName() + ".exporting");
        deleteWithSidecars(exportedFile);

        SQLiteDatabase sourceDb = new SQLiteDatabase(dbFile.getPath(), openKeySpec);
        try {
            sourceDb.executeFast("ATTACH DATABASE \"" + exportedFile.getPath() + "\" AS exported KEY " + attachKeyLiteral).stepThis().dispose();
            sourceDb.executeFast("SELECT sqlcipher_export('exported')").stepThis().dispose();
            // sqlcipher_export doesn't copy user_version, and MessagesStorage treats 0 as malformed
            Integer userVersion = sourceDb.executeInt("PRAGMA user_version");
            if (userVersion != null) {
                sourceDb.executeFast("PRAGMA exported.user_version = " + userVersion).stepThis().dispose();
            }
            sourceDb.executeFast("DETACH DATABASE exported").stepThis().dispose();
        } finally {
            sourceDb.close();
        }

        swapExportedFileIntoPlace(dbFile, exportedFile);
    }

    private static void swapExportedFileIntoPlace(File dbFile, File exportedFile) throws Exception {
        File backupFile = getBackupFile(dbFile);
        Utils.shredFile(backupFile);
        if (!dbFile.renameTo(backupFile)) {
            deleteWithSidecars(exportedFile);
            throw new IllegalStateException("failed to move the source database");
        }
        if (!exportedFile.renameTo(dbFile)) {
            backupFile.renameTo(dbFile);
            deleteWithSidecars(exportedFile);
            throw new IllegalStateException("failed to move the exported database");
        }
        Utils.shredFile(backupFile);
        Utils.shredFile(new File(dbFile.getPath() + "-wal"));
        Utils.shredFile(new File(dbFile.getPath() + "-shm"));
    }

    private static void restoreBackupIfNeeded(File dbFile) throws Exception {
        File backupFile = getBackupFile(dbFile);
        if (!backupFile.exists()) {
            return;
        }
        if (!dbFile.exists()) {
            backupFile.renameTo(dbFile);
        } else {
            Utils.shredFile(backupFile);
            Utils.shredFile(new File(dbFile.getPath() + "-wal"));
            Utils.shredFile(new File(dbFile.getPath() + "-shm"));
        }
    }

    private static File getBackupFile(File dbFile) {
        return new File(dbFile.getParentFile(), dbFile.getName() + ".export-backup");
    }

    public static void deleteWithSidecars(File dbFile) throws Exception {
        Utils.shredFile(dbFile);
        Utils.shredFile(new File(dbFile.getPath() + "-journal"));
        Utils.shredFile(new File(dbFile.getPath() + "-wal"));
        Utils.shredFile(new File(dbFile.getPath() + "-shm"));
    }
}
