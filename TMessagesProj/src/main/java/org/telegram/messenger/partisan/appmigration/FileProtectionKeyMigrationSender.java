package org.telegram.messenger.partisan.appmigration;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.partisan.PartisanLog;
import org.telegram.messenger.partisan.Utils;
import org.telegram.messenger.partisan.fileprotection.FileProtectionEncryptionKeyStore;

import java.io.File;
import java.nio.charset.StandardCharsets;

/**
 * Source-side half of the database encryption key migration (see FileProtectionKeyMigrationReceiver
 * for the target side). The wrapped key blobs migrate with the account preferences, but they are
 * useless in the target app: Android Keystore keys never leave the app that created them. So this
 * app serializes the raw keys for the encrypted migration zip, and the target app replaces each
 * transferred key with a fresh one wrapped by its own keystore, re-encrypting the database.
 */
public class FileProtectionKeyMigrationSender {
    static final String KEYS_FILE_NAME = "db_encryption_keys";

    /**
     * One "account:hexKey" line per account, e.g.:
     * 0:9f2b7a1c4e6d8035af12b6c7d4e9f0a1...
     * 2:3c1a0e9d8b7f6543210fedcba9876543...
     */
    public static byte[] serializeKeysForMigration() {
        StringBuilder builder = new StringBuilder();
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            byte[] key = FileProtectionEncryptionKeyStore.getKeyIfExists(a);
            if (key != null) {
                builder.append(a).append(':').append(Utilities.bytesToHex(key)).append('\n');
            }
        }
        if (builder.length() == 0) {
            return null;
        }
        return builder.toString().getBytes(StandardCharsets.US_ASCII);
    }

    public static void deleteStaleKeysFile() {
        try {
            Utils.shredFile(getKeysFile());
        } catch (Exception e) {
            PartisanLog.e("FileProtectionKeyMigrationSender: failed to shred the stale keys file", e);
        }
    }

    static File getKeysFile() {
        return new File(ApplicationLoader.getFilesDirFixed(), KEYS_FILE_NAME);
    }
}
