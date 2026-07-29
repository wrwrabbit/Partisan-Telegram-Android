package org.telegram.messenger.partisan.appmigration;

import org.telegram.messenger.Utilities;
import org.telegram.messenger.partisan.Utils;
import org.telegram.messenger.partisan.fileprotection.FileProtectionEncryptionKeyStore;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses the "account:hexKey" lines file produced by {@link KeyMigrationSender#serializeKeysForMigration},
 * shared by every {@link org.telegram.messenger.partisan.fileprotection.FileProtectionEncryptionKeyStore.KeyType}'s
 * target-side receiver.
 */
class MigratedKeyFile {

    // For callers where consuming the key isn't idempotent (e.g. a database rekey), so double-processing
    // after a crash must be impossible: removing the entry before using it means a crash can't process
    // the same key twice, at the cost of losing the only copy if the caller fails after this returns.
    static byte[] readAndRemoveKey(File keysFile, int account) throws Exception {
        String prefix = account + ":";
        List<String> lines = readLines(keysFile);
        List<String> remaining = new ArrayList<>();
        byte[] key = null;
        boolean removed = false;
        for (String line : lines) {
            if (!removed && line.startsWith(prefix)) {
                key = parseKey(line.substring(prefix.length()));
                removed = true;
            } else {
                remaining.add(line);
            }
        }
        if (removed) {
            if (remaining.isEmpty()) {
                Utils.shredFile(keysFile);
            } else {
                Utils.shredFileContent(keysFile);
                writeLines(keysFile, remaining);
            }
        }
        return key;
    }

    // For callers where re-consuming the same key is a no-op: reads without removing, so the entry
    // stays available to retry if the caller's own storage step fails after this returns.
    static byte[] readKey(File keysFile, int account) throws Exception {
        String prefix = account + ":";
        for (String line : readLines(keysFile)) {
            if (line.startsWith(prefix)) {
                return parseKey(line.substring(prefix.length()));
            }
        }
        return null;
    }

    private static byte[] parseKey(String hex) {
        try {
            byte[] key = Utilities.hexToBytes(hex.trim());
            return key != null && key.length == FileProtectionEncryptionKeyStore.KEY_LENGTH ? key : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static List<String> readLines(File file) throws Exception {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.US_ASCII))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isEmpty()) {
                    lines.add(line);
                }
            }
        }
        return lines;
    }

    private static void writeLines(File file, List<String> lines) throws Exception {
        StringBuilder builder = new StringBuilder();
        for (String line : lines) {
            builder.append(line).append('\n');
        }
        try (FileOutputStream stream = new FileOutputStream(file)) {
            stream.write(builder.toString().getBytes(StandardCharsets.US_ASCII));
        }
    }
}
