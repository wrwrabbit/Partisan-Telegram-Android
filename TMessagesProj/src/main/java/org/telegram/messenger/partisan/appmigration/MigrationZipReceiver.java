package org.telegram.messenger.partisan.appmigration;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.android.exoplayer2.util.Log;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.partisan.PartisanLog;
import org.telegram.messenger.partisan.update.AppVersion;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class MigrationZipReceiver {
    public interface ZipReceiverDelegate {
        void onFinish(String error);
    }

    private final Activity activity;
    private final Intent intent;
    private final ZipReceiverDelegate delegate;

    private MigrationZipReceiver(Activity activity, Intent intent, ZipReceiverDelegate delegate) {
        this.activity = activity;
        this.intent = intent;
        this.delegate = delegate;
    }

    public static void receiveZip(Activity activity, Intent intent, ZipReceiverDelegate delegate) {
        new Thread(() -> new MigrationZipReceiver(activity, intent, delegate).receiveZipInternal()).start();
    }

    private void receiveZipInternal() {
        try {
            if (finishReceivingMigrationIfNeed()) {
                return;
            }
            AndroidUtilities.runOnUIThread(() -> {
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.telegramDataReceived);
            });
            deleteSharedPrefs();
            ZipInputStream zipStream = createZipStream();
            unpackZip(zipStream);
            zipStream.close();
            //noinspection ResultOfrttMethodCallIgnored
            new File(activity.getFilesDir(), "updater_files_copied").createNewFile();
            finishReceivingMigration(null);
        } catch (Exception e) {
            PartisanLog.e("ReceiveDataFromOtherPtg", e);
            showMigrationReceiveError(e);
        }
    }

    private boolean finishReceivingMigrationIfNeed() {
        if (AppMigrator.appAlreadyHasAccounts()) {
            if (SharedConfig.filesCopiedFromOldTelegram) { // already migrated
                PartisanLog.d("appAlreadyHasAccounts: filesCopiedFromOldTelegram");
                finishReceivingMigration(null);
            } else {
                finishReceivingMigration("alreadyHasAccounts");
                PartisanLog.d("appAlreadyHasAccounts: error = alreadyHasAccounts");
            }
            return true;
        } else if (isSourceAppVersionGreater()) {
            finishReceivingMigration("srcVersionGreater");
            return true;
        }
        return false;
    }

    private boolean isSourceAppVersionGreater() {
        String srcVersionString = intent.getStringExtra("version");
        AppVersion srcVersion = AppVersion.parseVersion(srcVersionString);
        return srcVersion == null || srcVersion.greater(AppVersion.getCurrentVersion());
    }

    private void deleteSharedPrefs() {
        File prefsDir = new File(activity.getFilesDir().getParentFile(), "shared_prefs");
        if (prefsDir.exists()) {
            deleteFilesRecursive(prefsDir, false);
        }
    }

    private static void deleteFilesRecursive(@NonNull File fileOrDirectory, boolean deleteThis) {
        if (fileOrDirectory.isDirectory()) {
            File[] files = fileOrDirectory.listFiles();
            if (files != null) {
                for (File child : files) {
                    deleteFilesRecursive(child, true);
                }
            }
        }

        if (deleteThis) {
            //noinspection ResultOfMethodCallIgnored
            fileOrDirectory.delete();
        }
    }

    private @NonNull ZipInputStream createZipStream() throws FileNotFoundException, GeneralSecurityException {
        InputStream inputStream = createInputStream(intent);
        BufferedInputStream bufferedStream = new BufferedInputStream(inputStream);
        CipherInputStream cipherStream = new CipherInputStream(bufferedStream, createCipher());
        return new ZipInputStream(cipherStream);
    }

    private InputStream createInputStream(Intent intent) throws FileNotFoundException {
        InputStream inputStream;
        if (Build.VERSION.SDK_INT >= 24) {
            inputStream = activity.getContentResolver().openInputStream(intent.getData());
        } else {
            inputStream = new FileInputStream(intent.getData().getPath());
        }
        return inputStream;
    }

    private Cipher createCipher() throws GeneralSecurityException {
        byte[] passwordBytes = intent.getByteArrayExtra("zipPassword");
        SecretKey key = new SecretKeySpec(passwordBytes, "AES");

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
        cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(passwordBytes));
        return cipher;
    }

    private void unpackZip(ZipInputStream zipStream) throws IOException {
        ZipEntry zipEntry = zipStream.getNextEntry();
        while (zipEntry != null) {
            File newFile = createFileFromZipEntry(activity.getFilesDir(), zipEntry);
            if (zipEntry.isDirectory()) {
                if (!newFile.isDirectory() && !newFile.mkdirs()) {
                    throw new IOException("Failed to create directory " + newFile);
                }
            } else {
                File parent = newFile.getParentFile();
                if (parent == null || !parent.isDirectory() && !parent.mkdirs()) {
                    throw new IOException("Failed to create directory " + parent);
                }

                writeFileContent(zipStream, newFile);
            }
            zipEntry = zipStream.getNextEntry();
        }
    }

    private static File createFileFromZipEntry(File destinationDir, ZipEntry zipEntry) throws IOException {
        File destFile = new File(destinationDir, zipEntry.getName());

        String destDirPath = destinationDir.getCanonicalPath();
        String destFilePath = destFile.getCanonicalPath();

        if (!destFilePath.startsWith(destDirPath + File.separator)) {
            throw new IOException("Entry is outside of the target dir: " + zipEntry.getName());
        }

        return destFile;
    }

    private static void writeFileContent(ZipInputStream zipStream, File newFile) throws IOException {
        byte[] buffer = new byte[8192];
        FileOutputStream fileOutputStream = new FileOutputStream(newFile);
        while (true) {
            int len = zipStream.read(buffer);
            if (len <= 0) {
                break;
            }
            fileOutputStream.write(buffer, 0, len);
        }
        fileOutputStream.close();
    }

    private void finishReceivingMigration(String error) {
        if (delegate != null) {
            delegate.onFinish(error);
        }
    }

    private void showMigrationReceiveError(Exception ex) {
        Log.e("BasePermissionActivity", "Error", ex);
        AndroidUtilities.runOnUIThread(() -> {
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.telegramDataReceivingError);
            Toast.makeText(activity, "Error: " + ex.getMessage(), Toast.LENGTH_LONG).show();
        });
    }
}
