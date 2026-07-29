package org.telegram.messenger.partisan.fileprotection;

import android.os.Build;

import org.telegram.messenger.partisan.PartisanLog;
import org.telegram.messenger.partisan.appmigration.TgnetKeyMigrationReceiver;
import org.telegram.tgnet.ConnectionsManager;

public class FileProtectionTgnetEncryption {
    public static void applyConfigKey(int account) {
        try {
            TgnetKeyMigrationReceiver.takeOverMigratedTgnetKeyIfNeeded(account);
        } catch (Exception e) {
            PartisanLog.e("FileProtectionTgnetEncryption: failed to take over the migrated key", e);
        }
        byte[] key = null;
        boolean encryptOnWrite = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (FileProtectionUtils.authTokenEncryptionEnabledByConfig(account)) {
                // encryptOnWrite stays true even if the key is momentarily unavailable, so native
                // skips the write and preserves the encrypted file rather than clobbering it with plaintext.
                key = FileProtectionEncryptionKeyStore.getOrCreateKey(FileProtectionEncryptionKeyStore.KeyType.AUTH_TOKEN, account);
                encryptOnWrite = true;
            } else {
                // Disabled: still hand over an existing key so a currently-encrypted file can be read
                // and then rewritten as plaintext on the next save.
                key = FileProtectionEncryptionKeyStore.getKeyIfExists(FileProtectionEncryptionKeyStore.KeyType.AUTH_TOKEN, account);
            }
        }
        ConnectionsManager.native_setTgnetConfigKey(account, key, encryptOnWrite);
    }
}
