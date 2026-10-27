package org.telegram.messenger.partisan.appmigration;

import android.content.SharedPreferences;
import android.os.Build;

import org.telegram.messenger.partisan.PartisanLog;
import org.telegram.messenger.partisan.fileprotection.FileProtectionEncryptionKeyStore;
import org.telegram.messenger.partisan.fileprotection.FileProtectionEncryptionKeyStore.KeyType;

import java.io.File;

// Target-side half of the tgnet.dat key migration (see KeyMigrationSender for the source side).
public class TgnetKeyMigrationReceiver {
    // Once set, a blob for this account is ours, not a leftover from the source app's keystore.
    private static final String MIGRATION_TAKEN_OVER = "tgnetKeyMigrationTakenOver";

    public static synchronized void takeOverMigratedTgnetKeyIfNeeded(int account) throws Exception {
        File keysFile = KeyMigrationSender.getKeysFile(KeyType.AUTH_TOKEN);
        if (!keysFile.exists()) {
            return;
        }
        byte[] migratedKey = MigratedKeyFile.readKey(keysFile, account);
        if (migratedKey == null) {
            cleanUpForeignKeyBlobIfNeeded(account);
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            // No keystore below API 23, so the file was never encrypted; nothing to take over.
            MigratedKeyFile.readAndRemoveKey(keysFile, account);
            return;
        }
        // Store before removing: if storeKey fails, the key stays in the file to retry next launch.
        FileProtectionEncryptionKeyStore.storeKey(KeyType.AUTH_TOKEN, account, migratedKey);
        FileProtectionEncryptionKeyStore.getPreferences(KeyType.AUTH_TOKEN, account).edit()
                .putBoolean(MIGRATION_TAKEN_OVER, true).apply();
        MigratedKeyFile.readAndRemoveKey(keysFile, account);
        PartisanLog.d("TgnetKeyMigrationReceiver: took over the migrated tgnet key of account " + account);
    }

    // A blob that arrived with the migrated preferences but was never taken over is wrapped by the
    // source app's keystore and can never be unwrapped here.
    private static void cleanUpForeignKeyBlobIfNeeded(int account) {
        SharedPreferences preferences = FileProtectionEncryptionKeyStore.getPreferences(KeyType.AUTH_TOKEN, account);
        if (preferences.getBoolean(MIGRATION_TAKEN_OVER, false)) {
            return;
        }
        if (FileProtectionEncryptionKeyStore.keyBlobExists(KeyType.AUTH_TOKEN, account)
                && FileProtectionEncryptionKeyStore.getKeyIfExists(KeyType.AUTH_TOKEN, account) == null) {
            PartisanLog.e("TgnetKeyMigrationReceiver: account " + account + " has a foreign tgnet key blob but no migrated key");
            FileProtectionEncryptionKeyStore.deleteKey(KeyType.AUTH_TOKEN, account);
        }
    }

    public static void clearMigrationTakeoverMarker(int account) {
        FileProtectionEncryptionKeyStore.getPreferences(KeyType.AUTH_TOKEN, account).edit()
                .remove(MIGRATION_TAKEN_OVER).apply();
    }
}
