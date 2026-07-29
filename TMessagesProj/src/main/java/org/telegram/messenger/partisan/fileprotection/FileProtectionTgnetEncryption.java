package org.telegram.messenger.partisan.fileprotection;

import android.content.SharedPreferences;
import android.os.Build;

import org.telegram.messenger.partisan.PartisanLog;
import org.telegram.messenger.partisan.appmigration.TgnetKeyMigrationReceiver;
import org.telegram.messenger.partisan.fileprotection.FileProtectionEncryptionKeyStore.KeyType;
import org.telegram.tgnet.ConnectionsManager;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FileProtectionTgnetEncryption {
    public enum ConfigKeyState {
        READY,
        EXISTING_KEY_UNREADABLE,
        KEY_CREATION_FAILED
    }

    private static final String UNREADABLE_KEY_LAUNCH_COUNT = "unreadableConfigKeyLaunchCount";

    private static final Map<Integer, ConfigKeyState> configKeyStates = new ConcurrentHashMap<>();

    public static void applyConfigKey(int account) {
        try {
            TgnetKeyMigrationReceiver.takeOverMigratedTgnetKeyIfNeeded(account);
        } catch (Exception e) {
            PartisanLog.e("FileProtectionTgnetEncryption: failed to take over the migrated key", e);
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            // No keystore, so the file was never encrypted here. Looking for a key blob would let one
            // migrated from an API 23+ app pin encryptOnWrite on and block every config write.
            configKeyStates.put(account, ConfigKeyState.READY);
            ConnectionsManager.native_setTgnetConfigKey(account, null, false);
            return;
        }
        boolean encryptionEnabled = FileProtectionUtils.authTokenEncryptionEnabledByConfig(account);
        byte[] key = loadKey(account, encryptionEnabled);
        ConfigKeyState state = resolveConfigKeyState(account, key, encryptionEnabled);
        configKeyStates.put(account, state);
        updateUnreadableLaunchCount(account, state);
        if (state != ConfigKeyState.READY) {
            PartisanLog.e("FileProtectionTgnetEncryption: account " + account + " config key state is " + state);
        }
        ConnectionsManager.native_setTgnetConfigKey(account, key, needEncryptOnWrite(state, key, encryptionEnabled));
    }

    private static byte[] loadKey(int account, boolean encryptionEnabled) {
        if (encryptionEnabled) {
            return FileProtectionEncryptionKeyStore.getOrCreateKey(KeyType.AUTH_TOKEN, account);
        } else {
            // Disabled: still hand over an existing key so a currently-encrypted file can be read
            // and then rewritten as plaintext on the next save.
            return FileProtectionEncryptionKeyStore.getKeyIfExists(KeyType.AUTH_TOKEN, account);
        }
    }

    private static ConfigKeyState resolveConfigKeyState(int account, byte[] key, boolean encryptionEnabled) {
        if (key != null) {
            return ConfigKeyState.READY;
        } else if (FileProtectionEncryptionKeyStore.keyBlobExists(KeyType.AUTH_TOKEN, account)) {
            return ConfigKeyState.EXISTING_KEY_UNREADABLE;
        } else if (encryptionEnabled) {
            return ConfigKeyState.KEY_CREATION_FAILED;
        } else {
            return ConfigKeyState.READY;
        }
    }

    private static boolean needEncryptOnWrite(ConfigKeyState state, byte[] key, boolean encryptionEnabled) {
        switch (state) {
            case EXISTING_KEY_UNREADABLE:
                // There is no key, but tgnet.dat may be encrypted with it. Native skips every write
                // while this is set, preserving the file for a launch where the keystore works again.
                return true;
            case KEY_CREATION_FAILED:
                // Nothing is encrypted yet, so skipping writes loses no data - it only stops the
                // session from persisting. The user chooses which of the two they prefer.
                return !FileProtectionSettings.storeAuthTokenUnencryptedWhenKeyUnavailable.get().orElse(false);
            default:
                return key != null && encryptionEnabled;
        }
    }

    // Counts consecutive launches that failed to unwrap the key, so the UI can tell a keystore hiccup
    // that a restart clears from a key that is gone for good.
    private static void updateUnreadableLaunchCount(int account, ConfigKeyState state) {
        SharedPreferences preferences = FileProtectionEncryptionKeyStore.getPreferences(KeyType.AUTH_TOKEN, account);
        if (state == ConfigKeyState.EXISTING_KEY_UNREADABLE) {
            int launchCount = preferences.getInt(UNREADABLE_KEY_LAUNCH_COUNT, 0);
            preferences.edit().putInt(UNREADABLE_KEY_LAUNCH_COUNT, launchCount + 1).apply();
        } else if (preferences.contains(UNREADABLE_KEY_LAUNCH_COUNT)) {
            preferences.edit().remove(UNREADABLE_KEY_LAUNCH_COUNT).apply();
        }
    }

    public static ConfigKeyState getConfigKeyState(int account) {
        ConfigKeyState state = configKeyStates.get(account);
        return state != null ? state : ConfigKeyState.READY;
    }

    public static boolean isExistingConfigKeyUnreadable(int account) {
        return getConfigKeyState(account) == ConfigKeyState.EXISTING_KEY_UNREADABLE;
    }

    public static boolean isConfigKeyUnreadableAcrossRestarts(int account) {
        return isExistingConfigKeyUnreadable(account)
                && FileProtectionEncryptionKeyStore.getPreferences(KeyType.AUTH_TOKEN, account).getInt(UNREADABLE_KEY_LAUNCH_COUNT, 0) > 1;
    }

    // The key and tgnet.dat are gone, so the recorded diagnosis no longer describes this account.
    public static void clearConfigKeyState(int account) {
        configKeyStates.remove(account);
        FileProtectionEncryptionKeyStore.getPreferences(KeyType.AUTH_TOKEN, account).edit().remove(UNREADABLE_KEY_LAUNCH_COUNT).apply();
    }
}
