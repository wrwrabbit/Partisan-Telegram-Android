package org.telegram.messenger.partisan.fileprotection;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.security.keystore.StrongBoxUnavailableException;
import android.util.Base64;

import androidx.annotation.RequiresApi;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.partisan.PartisanLog;

import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Stores a random per-account encryption key. The raw key is needed by consumers that can't use a
 * Keystore key directly (SQLCipher's sqlite3_key needs exportable material; native tgnet code can't
 * reach the Android Keystore at all), so it is wrapped with AES-GCM using a non-exportable Keystore
 * key and the wrapped blob is persisted in per-account preferences. Each {@link KeyType} is an
 * independent key with its own Keystore alias and preference so their lifecycles never interfere.
 */
public class FileProtectionEncryptionKeyStore {
    public enum KeyType {
        DATABASE("FileProtectionDbKey", "dbEncryptionKey", "db_encryption_keys", null),
        AUTH_TOKEN("AuthTokenKey", "authTokenEncryptionKey", "tgnet_encryption_keys", "fileprotection_keys");

        final String keystoreAlias;
        final String prefKey;
        public final String migrationFileName;
        final String prefsName;

        KeyType(String keystoreAlias, String prefKey, String migrationFileName, String prefsName) {
            this.keystoreAlias = keystoreAlias;
            this.prefKey = prefKey;
            this.migrationFileName = migrationFileName;
            this.prefsName = prefsName;
        }
    }

    public static final int KEY_LENGTH = 32;
    private static final int GCM_TAG_LENGTH = 128;

    public static synchronized byte[] getOrCreateKey(KeyType type, int account) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return null;
        }
        byte[] key = getKeyIfExists(type, account);
        if (key != null) {
            return key;
        }
        if (keyBlobExists(type, account)) {
            // The wrapped key exists but couldn't be unwrapped (temporary keystore failure).
            // Don't overwrite it: the data may already be encrypted with it.
            return null;
        }
        try {
            key = new byte[KEY_LENGTH];
            new SecureRandom().nextBytes(key);
            storeKey(type, account, key);
            return key;
        } catch (Exception e) {
            PartisanLog.e("FileProtectionEncryptionKeyStore: failed to create a key", e);
            return null;
        }
    }

    public static synchronized byte[] getKeyIfExists(KeyType type, int account) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return null;
        }
        try {
            String encoded = getPreferences(type, account).getString(type.prefKey, null);
            if (encoded == null) {
                return null;
            }
            return unwrapKey(type, Base64.decode(encoded, Base64.DEFAULT));
        } catch (Exception e) {
            PartisanLog.e("FileProtectionEncryptionKeyStore: failed to unwrap the key", e);
            return null;
        }
    }

    public static synchronized boolean keyBlobExists(KeyType type, int account) {
        return getPreferences(type, account).getString(type.prefKey, null) != null;
    }

    public static synchronized void deleteKey(KeyType type, int account) {
        getPreferences(type, account).edit().remove(type.prefKey).commit();
    }

    @RequiresApi(Build.VERSION_CODES.M)
    public static synchronized void storeKey(KeyType type, int account, byte[] key) throws Exception {
        byte[] blob = wrapKey(type, key);
        boolean saved = getPreferences(type, account).edit()
                .putString(type.prefKey, Base64.encodeToString(blob, Base64.DEFAULT))
                .commit();
        if (!saved) {
            throw new IllegalStateException("failed to save the wrapped key");
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private static byte[] wrapKey(KeyType type, byte[] key) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey(type));
        byte[] iv = cipher.getIV();
        byte[] wrappedKey = cipher.doFinal(key);
        byte[] blob = new byte[1 + iv.length + wrappedKey.length];
        blob[0] = (byte) iv.length;
        System.arraycopy(iv, 0, blob, 1, iv.length);
        System.arraycopy(wrappedKey, 0, blob, 1 + iv.length, wrappedKey.length);
        return blob;
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private static byte[] unwrapKey(KeyType type, byte[] blob) throws Exception {
        int ivLength = blob[0] & 0xFF;
        byte[] iv = Arrays.copyOfRange(blob, 1, 1 + ivLength);
        byte[] wrappedKey = Arrays.copyOfRange(blob, 1 + ivLength, blob.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, getSecretKeyIfExists(type), new GCMParameterSpec(GCM_TAG_LENGTH, iv));
        return cipher.doFinal(wrappedKey);
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private static SecretKey getOrCreateSecretKey(KeyType type) throws Exception {
        KeyStore keyStore = loadKeyStore();
        if (!keyStore.containsAlias(type.keystoreAlias)) {
            generateSecretKey(type);
        }
        return (SecretKey) keyStore.getKey(type.keystoreAlias, null);
    }

    // Never generates a key: a missing alias while a wrapped blob exists means the blob is already
    // lost, and a replacement would silently make it undecryptable forever instead of just now.
    @RequiresApi(Build.VERSION_CODES.M)
    private static SecretKey getSecretKeyIfExists(KeyType type) throws Exception {
        SecretKey key = (SecretKey) loadKeyStore().getKey(type.keystoreAlias, null);
        if (key == null) {
            throw new IllegalStateException("the keystore key is missing");
        }
        return key;
    }

    private static KeyStore loadKeyStore() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        return keyStore;
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private static void generateSecretKey(KeyType type) throws Exception {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                generateSecretKey(type, true);
                return;
            } catch (StrongBoxUnavailableException e) {
                PartisanLog.d("FileProtectionEncryptionKeyStore: StrongBox is unavailable, falling back to TEE");
            }
        }
        generateSecretKey(type, false);
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private static void generateSecretKey(KeyType type, boolean strongBoxBacked) throws Exception {
        KeyGenerator keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec.Builder(type.keystoreAlias, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(false);
        if (strongBoxBacked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.setIsStrongBoxBacked(true);
        }
        keyGenerator.init(builder.build());
        keyGenerator.generateKey();
    }

    public static SharedPreferences getPreferences(KeyType type, int account) {
        if (type.prefsName == null) {
            return UserConfig.getInstance(account).getPreferences();
        }
        String name = account == 0 ? type.prefsName : type.prefsName + account;
        return ApplicationLoader.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE);
    }
}
