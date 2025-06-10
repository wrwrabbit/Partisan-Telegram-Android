package org.telegram.messenger.partisan.appmigration;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.android.exoplayer2.util.Log;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.fakepasscode.FakePasscode;
import org.telegram.messenger.partisan.PartisanLog;
import org.telegram.messenger.partisan.masked_ptg.MaskedPtgConfig;
import org.telegram.messenger.partisan.update.AppVersion;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

public class MigrationZipReceiver {
    public interface ZipReceiverDelegate {
        void onFinish(String error, Set<String> issues);
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
        PartisanLog.d("MigrationZipReceiver: 0");
        new Thread(() -> new MigrationZipReceiver(activity, intent, delegate).receiveZipInternal()).start();
    }

    private void receiveZipInternal() {
        try {
            PartisanLog.d("MigrationZipReceiver: 1");
            if (finishReceivingMigrationIfNeed()) {
                PartisanLog.d("MigrationZipReceiver: finishMigration");
                return;
            }
            PartisanLog.d("MigrationZipReceiver: 2");
            deleteSharedPrefs();
            PartisanLog.d("MigrationZipReceiver: 3");
            ZipInputStream zipStream = createZipStream();
            PartisanLog.d("MigrationZipReceiver: 4");
            unpackZip(zipStream);
            PartisanLog.d("MigrationZipReceiver: 5");
            zipStream.close();
            PartisanLog.d("MigrationZipReceiver: 6");
            //noinspection ResultOfrttMethodCallIgnored
            new File(activity.getFilesDir(), "updater_files_copied").createNewFile();
            PartisanLog.d("MigrationZipReceiver: 7");
            finishReceivingMigration(null);
            PartisanLog.d("MigrationZipReceiver: 8");
        } catch (Exception e) {
            PartisanLog.e("ReceiveDataFromOtherPtg", e);
            showMigrationReceiveError(e);
        }
    }

    private boolean finishReceivingMigrationIfNeed() {
        if (AppMigrator.appAlreadyHasAccounts()) {
            if (SharedConfig.filesCopiedFromOldTelegram) { // already migrated
                PartisanLog.d("MigrationZipReceiver: appAlreadyHasAccounts: filesCopiedFromOldTelegram");
                finishReceivingMigration(null);
            } else {
                finishReceivingMigration("alreadyHasAccounts");
                PartisanLog.d("MigrationZipReceiver: appAlreadyHasAccounts: error = alreadyHasAccounts");
            }
            return true;
        } else if (isSourceAppVersionGreater()) {
            PartisanLog.d("MigrationZipReceiver: isSourceAppVersionGreater");
            finishReceivingMigration("srcVersionGreater");
            return true;
        }
        PartisanLog.d("MigrationZipReceiver: getZipIssues");
        Set<String> zipIssues = getZipIssues();
        if (!zipIssues.isEmpty()) {
            PartisanLog.d("MigrationZipReceiver: !zipIssues.isEmpty()");
            finishReceivingMigration("settingsDoNotSuitMaskedApps", zipIssues);
            return true;
        }
        return false;
    }

    private boolean isSourceAppVersionGreater() {
        String srcVersionString = intent.getStringExtra("version");
        AppVersion srcVersion = AppVersion.parseVersion(srcVersionString);
        return srcVersion == null || srcVersion.greater(AppVersion.getCurrentVersion());
    }

    private @NonNull Set<String> getZipIssues() {
        Document config = extractXmlDocumentFromZip("userconfing.xml");
        if (config == null) {
            return Collections.emptySet();
        }
        Set<String> issues = new HashSet<>();
        if (!validatePasscodeType(config)) {
            issues.add(MaskedMigrationIssue.INVALID_PASSCODE_TYPE.toString());
        }
        if (!validateMainPasscodeFingerprint(config)) {
            issues.add(MaskedMigrationIssue.ACTIVATE_BY_FINGERPRINT.toString());
        }

        issues.addAll(getFakePasscodesIssues(config));
        return issues;
    }

    private boolean validateMainPasscodeFingerprint(Document config) {
        if (MaskedPtgConfig.allowFingerprint()) {
            return true;
        }
        String type = getPreferenceValueFromAttribute(config, "boolean", "useFingerprint");
        return !Objects.equals(type, "true");
    }

    private boolean validatePasscodeType(Document config) {
        if (MaskedPtgConfig.allowAlphaNumericPassword()) {
            return true;
        }
        String type = getPreferenceValueFromAttribute(config, "int", "passcodeType");
        return Objects.equals(type, "0");
    }

    private String getPreferenceValueFromAttribute(Document config, String nodeType, String name) {
        Node preferenceNode = getPreferenceNodeFromConfig(config, nodeType, name);
        if (preferenceNode == null) {
            return null;
        }
        return preferenceNode.getAttributes().getNamedItem("value").getNodeValue();
    }

    private Node getPreferenceNodeFromConfig(Document config, String nodeType, String name) {
        NodeList nodeList = config.getElementsByTagName(nodeType);
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node tagNode = nodeList.item(i);
            if (tagNode.getAttributes().getNamedItem("name").getNodeValue().equals(name)) {
                return tagNode;
            }
        }
        return null;
    }

    private @NonNull Set<String> getFakePasscodesIssues(Document config) {
        List<FakePasscode> fakePasscodes = extractFakePasscodesFromConfig(config);
        if (fakePasscodes == null) {
            return Collections.emptySet();
        }
        Set<String> issues = new HashSet<>();
        for (FakePasscode fakePasscode : fakePasscodes) {
            if (fakePasscode.passwordlessMode) {
                issues.add(MaskedMigrationIssue.PASSWORDLESS_MODE.toString());
            }
            if (!MaskedPtgConfig.allowFingerprint() && fakePasscode.activateByFingerprint) {
                issues.add(MaskedMigrationIssue.ACTIVATE_BY_FINGERPRINT.toString());
            }
        }
        return issues;
    }

    private List<FakePasscode> extractFakePasscodesFromConfig(Document config) {
        Node preferenceNode = getPreferenceNodeFromConfig(config, "string", "fakePasscodes");
        if (preferenceNode == null) {
            return null;
        }
        Node childNode = preferenceNode.getFirstChild();
        String fakePasscodesString = childNode.getNodeValue();
        try {
            SharedConfig.FakePasscodesWrapper wrapper = SharedConfig.fromJson(fakePasscodesString, SharedConfig.FakePasscodesWrapper.class);
            return wrapper.fakePasscodes;
        } catch (JsonProcessingException e) {
            PartisanLog.e("ReceiveDataFromOtherPtg", e);
            return null;
        }
    }

    private Document extractXmlDocumentFromZip(String filename) {
        try {
            ZipInputStream zipStream = createZipStream();
            ZipEntry zipEntry = findZipEntry(filename, zipStream);
            if (zipEntry == null) {
                return null;
            }
            Document document = readXmlZipEntry(zipStream);
            zipStream.close();
            return document;
        } catch (Exception e) {
            PartisanLog.e("ReceiveDataFromOtherPtg", e);
            return null;
        }
    }

    private static ZipEntry findZipEntry(String filename, ZipInputStream zipStream) throws IOException {
        ZipEntry zipEntry = zipStream.getNextEntry();
        while (zipEntry != null) {
            if (zipEntry.getName() == null) {
                continue;
            }
            String entryFilename = new File(zipEntry.getName()).getName();
            if (Objects.equals(entryFilename, filename)) {
               return zipEntry;
            }
            zipEntry = zipStream.getNextEntry();
        }
        return null;
    }

    private static Document readXmlZipEntry(ZipInputStream zipStream) throws IOException, SAXException, ParserConfigurationException {
        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
        return documentBuilder.parse(zipStream);
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
            PartisanLog.d("MigrationZipReceiver: unpack zip entry");
            File newFile = createFileFromZipEntry(activity.getFilesDir(), zipEntry);
            if (zipEntry.isDirectory()) {
                PartisanLog.d("MigrationZipReceiver: unpack zip directory");
                if (!newFile.isDirectory() && !newFile.mkdirs()) {
                    throw new IOException("Failed to create directory " + newFile);
                }
            } else {
                PartisanLog.d("MigrationZipReceiver: unpack zip file");
                File parent = newFile.getParentFile();
                if (parent == null || !parent.isDirectory() && !parent.mkdirs()) {
                    throw new IOException("Failed to create directory " + parent);
                }

                writeFileContent(zipStream, newFile);
            }
            zipEntry = zipStream.getNextEntry();
        }
        PartisanLog.d("MigrationZipReceiver: zip unpacked");
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
        PartisanLog.d("MigrationZipReceiver: write file content");
        while (true) {
            int len = zipStream.read(buffer);
            if (len <= 0) {
                break;
            }
            fileOutputStream.write(buffer, 0, len);
        }
        PartisanLog.d("MigrationZipReceiver: write file content finished");
        fileOutputStream.close();
    }

    private void finishReceivingMigration(String error) {
        finishReceivingMigration(error, null);
    }

    private void finishReceivingMigration(String error, Set<String> issues) {
        if (delegate != null) {
            delegate.onFinish(error, issues);
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
