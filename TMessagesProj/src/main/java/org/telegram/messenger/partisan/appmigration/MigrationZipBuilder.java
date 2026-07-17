package org.telegram.messenger.partisan.appmigration;

import android.content.Context;
import android.content.ContextWrapper;
import android.net.Uri;
import android.os.Build;

import androidx.core.content.FileProvider;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.partisan.PartisanLog;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class MigrationZipBuilder {
    private static File zipFile;
    private static byte[] passwordBytes;

    public interface MakeZipDelegate {
        void makeZipCompleted();
        void makeZipFailed();
    }

    private static class MakeZipException extends Exception {
    }

    public static void makeZip(ContextWrapper contextWrapper, MakeZipDelegate delegate) {
        try {
            byte[] passwordBytes = new byte[16];
            Utilities.random.nextBytes(passwordBytes);
            File zipFile = makeDataZip(contextWrapper, passwordBytes);
            if (zipFile == null) {
                return;
            }
            MigrationZipBuilder.zipFile = zipFile;
            MigrationZipBuilder.passwordBytes = passwordBytes;
            delegate.makeZipCompleted();
        } catch (MakeZipException e) {
            PartisanLog.e("MoveDataToOtherPtg", e);
            delegate.makeZipFailed();
        } catch (Exception e) {
            delegate.makeZipFailed();
            PartisanLog.e("MoveDataToOtherPtg", e);
        }
    }

    private static String buildPath(String path, String file) {
        if (path == null || path.isEmpty()) {
            return file;
        } else {
            return path + "/" + file;
        }
    }

    private static void addDirToZip(ZipOutputStream zos, String path, File dir) throws IOException {
        if (!dir.canRead()) {
            return;
        }

        File[] files = dir.listFiles();
        path = buildPath(path, dir.getName());

        if (files != null) {
            for (File source : files) {
                if (source.isDirectory()) {
                    addDirToZip(zos, path, source);
                } else {
                    addFileToZip(zos, path, source);
                }
            }
        }
    }

    private static void addFileToZip(ZipOutputStream zos, String path, File file) throws IOException {
        if (!file.canRead()) {
            return;
        }

        zos.putNextEntry(new ZipEntry(buildPath(path, file.getName())));

        FileInputStream fis = new FileInputStream(file);

        byte[] buffer = new byte[4092];
        int byteCount;
        while ((byteCount = fis.read(buffer)) != -1) {
            zos.write(buffer, 0, byteCount);
        }

        fis.close();
        zos.closeEntry();
    }

    private static File makeDataZip(ContextWrapper contextWrapper, byte[] passwordBytes) throws Exception {
        File externalFilesDir = getExternalFilesDir();
        if (externalFilesDir == null) {
            return null;
        }

        File zipFile;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            zipFile = new File(externalFilesDir, "data.zip");
        } else {
            zipFile = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_DOCUMENT), "data.zip");
        }
        if (zipFile.exists()) {
            if (!zipFile.delete()) {
                throw new MakeZipException();
            }
        }
        if (!zipFile.createNewFile()) {
            throw new MakeZipException();
        }

        SecretKey key = new SecretKeySpec(passwordBytes, "AES");

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
        cipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(passwordBytes));

        FileOutputStream fileStream = new FileOutputStream(zipFile);
        BufferedOutputStream bufferedStream = new BufferedOutputStream(fileStream);
        CipherOutputStream cipherStream = new CipherOutputStream(bufferedStream, cipher);
        ZipOutputStream zipStream = new ZipOutputStream(cipherStream);

        File filesDir = contextWrapper.getFilesDir();
        FileProtectionKeyMigrationSender.deleteStaleKeysFile();
        addDirToZip(zipStream, "", filesDir);
        addDirToZip(zipStream, "", new File(filesDir.getParentFile(), "shared_prefs"));
        addDbEncryptionKeysToZip(zipStream);
        zipStream.close();
        return zipFile;
    }

    // The database encryption keys are wrapped by this app's Android Keystore, which the
    // target app can't access, so the raw keys are transferred inside the migration zip.
    // Inside the zip they're never unencrypted on disk (the zip stream is wrapped in a
    // CipherOutputStream); once extracted on the target they exist briefly as a plaintext
    // file, which FileProtectionKeyMigrationReceiver shreds as each key is consumed.
    private static void addDbEncryptionKeysToZip(ZipOutputStream zos) throws IOException {
        byte[] keysFileContent = FileProtectionKeyMigrationSender.serializeKeysForMigration();
        if (keysFileContent == null) {
            return;
        }
        zos.putNextEntry(new ZipEntry("files/" + FileProtectionKeyMigrationSender.KEYS_FILE_NAME));
        zos.write(keysFileContent);
        zos.closeEntry();
    }

    public static void deleteZipFile() {
        File zipFile;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            File externalFilesDir = getExternalFilesDir();
            if (externalFilesDir == null) {
                return;
            }
            zipFile = new File(externalFilesDir, "data.zip");
        } else {
            zipFile = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_DOCUMENT), "data.zip");
        }
        if (zipFile.exists()) {
            zipFile.delete();
        }
    }

    private static File getExternalFilesDir() {
        File externalFilesDir = ApplicationLoader.applicationContext.getExternalFilesDir(null);
        if (!externalFilesDir.exists() && !externalFilesDir.mkdirs()) {
            return null;
        }
        return externalFilesDir;
    }

    public static Uri getZipUri(Context context) {
        if (zipFileExists()) {
            return fileToUri(zipFile, context);
        } else {
            return null;
        }
    }

    private static Uri fileToUri(File file, Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return FileProvider.getUriForFile(context, ApplicationLoader.getApplicationId() + ".provider", file);
        } else {
            return Uri.fromFile(file);
        }
    }

    public static byte[] getPasswordBytes() {
        return passwordBytes;
    }

    public static boolean zipFileExists() {
        return zipFile != null && zipFile.exists();
    }
}
