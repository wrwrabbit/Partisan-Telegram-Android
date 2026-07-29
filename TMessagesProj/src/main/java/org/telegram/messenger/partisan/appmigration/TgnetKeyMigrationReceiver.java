package org.telegram.messenger.partisan.appmigration;

import android.os.Build;

import org.telegram.messenger.partisan.PartisanLog;
import org.telegram.messenger.partisan.fileprotection.FileProtectionEncryptionKeyStore;
import org.telegram.messenger.partisan.fileprotection.FileProtectionEncryptionKeyStore.KeyType;

import java.io.File;

/**
 * Target-side half of the tgnet.dat encryption key migration (see KeyMigrationSender for the source
 * side). Re-wraps each transferred raw key under this app's own keystore WITHOUT re-encrypting
 * the file: the raw key is unchanged, so the migrated (still-encrypted) tgnet.dat decrypts with it.
 */
public class TgnetKeyMigrationReceiver {

    /**
     * Called before native tgnet init. If a migrated keys file is present, re-wraps the account's
     * transferred key under this app's keystore so the migrated tgnet.dat can be decrypted. A foreign
     * wrapped blob that arrived with the migrated preferences (never unwrappable here) is removed.
     */
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
        // Re-wrapping the same raw key twice is a no-op, so store before removing: if storeKey fails
        // (keystore hiccup, failed commit), the key stays in the file to retry next launch instead
        // of being lost with the only copy gone.
        FileProtectionEncryptionKeyStore.storeKey(KeyType.AUTH_TOKEN, account, migratedKey);
        MigratedKeyFile.readAndRemoveKey(keysFile, account);
        PartisanLog.d("TgnetKeyMigrationReceiver: took over the migrated tgnet key of account " + account);
    }

    // No key was migrated for this account, but a key blob may have arrived with the migrated
    // preferences. It was wrapped by the source app's keystore and can never be unwrapped here.
    private static void cleanUpForeignKeyBlobIfNeeded(int account) {
        if (FileProtectionEncryptionKeyStore.keyBlobExists(KeyType.AUTH_TOKEN, account)
                && FileProtectionEncryptionKeyStore.getKeyIfExists(KeyType.AUTH_TOKEN, account) == null) {
            PartisanLog.e("TgnetKeyMigrationReceiver: account " + account + " has a foreign tgnet key blob but no migrated key");
            FileProtectionEncryptionKeyStore.deleteKey(KeyType.AUTH_TOKEN, account);
        }
    }
}
