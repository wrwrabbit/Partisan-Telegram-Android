package org.telegram.messenger.partisan.fileprotection;

import android.content.SharedPreferences;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.security.keystore.StrongBoxUnavailableException;
import android.util.Base64;

import androidx.annotation.RequiresApi;

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
 * Stores a random per-account database encryption key. The raw key is needed by SQLCipher
 * (sqlite3_key requires exportable key material), so it can't live in the Android Keystore
 * directly. Instead, it is wrapped with AES-GCM using a non-exportable Keystore key and the
 * wrapped blob is persisted in the account's preferences.
 */
public class FileProtectionEncryptionKeyStore {
    private static final String KEYSTORE_ALIAS = "FileProtectionDbKey";
    private static final String PREF_KEY = "dbEncryptionKey";
    public static final int DB_KEY_LENGTH = 32;
    private static final int GCM_TAG_LENGTH = 128;

    public static synchronized byte[] getOrCreateKey(int account) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return null;
        }
        byte[] key = getKeyIfExists(account);
        if (key != null) {
            return key;
        }
        if (keyBlobExists(account)) {
            // The wrapped key exists but couldn't be unwrapped (temporary keystore failure).
            // Don't overwrite it: the database may already be encrypted with it.
            return null;
        }
        try {
            key = new byte[DB_KEY_LENGTH];
            new SecureRandom().nextBytes(key);
            storeKey(account, key);
            return key;
        } catch (Exception e) {
            PartisanLog.e("FileProtectionEncryptionKeyStore: failed to create a database key", e);
            return null;
        }
    }

    public static synchronized byte[] getKeyIfExists(int account) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return null;
        }
        try {
            String encoded = getPreferences(account).getString(PREF_KEY, null);
            if (encoded == null) {
                return null;
            }
            return unwrapKey(Base64.decode(encoded, Base64.DEFAULT));
        } catch (Exception e) {
            PartisanLog.e("FileProtectionEncryptionKeyStore: failed to unwrap the database key", e);
            return null;
        }
    }

    public static synchronized boolean keyBlobExists(int account) {
        return getPreferences(account).getString(PREF_KEY, null) != null;
    }

    public static synchronized void deleteKey(int account) {
        getPreferences(account).edit().remove(PREF_KEY).commit();
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private static void storeKey(int account, byte[] key) throws Exception {
        byte[] blob = wrapKey(key);
        boolean saved = getPreferences(account).edit()
                .putString(PREF_KEY, Base64.encodeToString(blob, Base64.DEFAULT))
                .commit();
        if (!saved) {
            throw new IllegalStateException("failed to save the wrapped database key");
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private static byte[] wrapKey(byte[] key) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey());
        byte[] iv = cipher.getIV();
        byte[] wrappedKey = cipher.doFinal(key);
        byte[] blob = new byte[1 + iv.length + wrappedKey.length];
        blob[0] = (byte) iv.length;
        System.arraycopy(iv, 0, blob, 1, iv.length);
        System.arraycopy(wrappedKey, 0, blob, 1 + iv.length, wrappedKey.length);
        return blob;
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private static byte[] unwrapKey(byte[] blob) throws Exception {
        int ivLength = blob[0] & 0xFF;
        byte[] iv = Arrays.copyOfRange(blob, 1, 1 + ivLength);
        byte[] wrappedKey = Arrays.copyOfRange(blob, 1 + ivLength, blob.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), new GCMParameterSpec(GCM_TAG_LENGTH, iv));
        return cipher.doFinal(wrappedKey);
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private static SecretKey getSecretKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        if (!keyStore.containsAlias(KEYSTORE_ALIAS)) {
            generateSecretKey();
        }
        return (SecretKey) keyStore.getKey(KEYSTORE_ALIAS, null);
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private static void generateSecretKey() throws Exception {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                generateSecretKey(true);
                return;
            } catch (StrongBoxUnavailableException e) {
                PartisanLog.d("FileProtectionEncryptionKeyStore: StrongBox is unavailable, falling back to TEE");
            }
        }
        generateSecretKey(false);
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private static void generateSecretKey(boolean strongBoxBacked) throws Exception {
        KeyGenerator keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec.Builder(KEYSTORE_ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(false);
        if (strongBoxBacked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.setIsStrongBoxBacked(true);
        }
        keyGenerator.init(builder.build());
        keyGenerator.generateKey();
    }

    private static SharedPreferences getPreferences(int account) {
        return UserConfig.getInstance(account).getPreferences();
    }
}
