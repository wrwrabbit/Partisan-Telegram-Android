package org.telegram.messenger.partisan.appmigration;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.partisan.PartisanLog;
import org.telegram.messenger.partisan.Utils;
import org.telegram.messenger.partisan.fileprotection.FileProtectionEncryptionKeyStore;
import org.telegram.messenger.partisan.fileprotection.FileProtectionEncryptionKeyStore.KeyType;

import java.io.File;
import java.nio.charset.StandardCharsets;

/**
 * Source-side half of the encryption key migration, for every {@link KeyType} (see
 * DatabaseKeyMigrationReceiver and TgnetKeyMigrationReceiver for the target-side halves).
 * Android Keystore keys never leave the app that created them, so the raw per-account keys of each
 * type are serialized into their own file inside the encrypted migration zip.
 */
public class KeyMigrationSender {

    /**
     * One "account:hexKey" line per account, e.g.:
     * 0:9f2b7a1c4e6d8035af12b6c7d4e9f0a1...
     * 2:3c1a0e9d8b7f6543210fedcba9876543...
     */
    public static byte[] serializeKeysForMigration(KeyType type) {
        StringBuilder builder = new StringBuilder();
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            byte[] key = FileProtectionEncryptionKeyStore.getKeyIfExists(type, a);
            if (key != null) {
                builder.append(a).append(':').append(Utilities.bytesToHex(key)).append('\n');
            }
        }
        if (builder.length() == 0) {
            return null;
        }
        return builder.toString().getBytes(StandardCharsets.US_ASCII);
    }

    public static void deleteStaleKeysFile(KeyType type) {
        try {
            Utils.shredFile(getKeysFile(type));
        } catch (Exception e) {
            PartisanLog.e("KeyMigrationSender: failed to shred the stale " + type + " keys file", e);
        }
    }

    static File getKeysFile(KeyType type) {
        return new File(ApplicationLoader.getFilesDirFixed(), type.migrationFileName);
    }
}
