package org.telegram.messenger.fakepasscode;

import android.util.Base64;

import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;

public class AccountIdHashUtils {
    public static String generateSalt() {
        byte[] saltBytes = new byte[16];
        Utilities.random.nextBytes(saltBytes);
        return Base64.encodeToString(saltBytes, Base64.DEFAULT);
    }

    public static String calculateIdHash(TLRPC.User user, String saltBase64) {
        String phoneDigits = user.phone.replaceAll("[^0-9]", "");
        if (phoneDigits.length() < 4) {
            throw new RuntimeException("Can't calculate id hash: invalid phone");
        }
        int phoneId = Integer.parseInt(phoneDigits.substring(phoneDigits.length() - 4));
        long sum = (user.id % 10_000 + phoneId) % 10_000;
        byte[] sumBytes = Long.toString(sum).getBytes();
        byte[] salt = Base64.decode(saltBase64, Base64.DEFAULT);
        byte[] bytes = new byte[32 + sumBytes.length];
        System.arraycopy(salt, 0, bytes, 0, 16);
        System.arraycopy(sumBytes, 0, bytes, 16, sumBytes.length);
        System.arraycopy(salt, 0, bytes, sumBytes.length + 16, 16);
        return Utilities.bytesToHex(Utilities.computeSHA256(bytes, 0, bytes.length));
    }
}
