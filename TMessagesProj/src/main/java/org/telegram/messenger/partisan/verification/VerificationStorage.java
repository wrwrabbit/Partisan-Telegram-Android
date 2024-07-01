package org.telegram.messenger.partisan.verification;

import androidx.annotation.Keep;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class VerificationStorage {
    private static final int minCheckDelay = 2 * 60 * 60;
    private static final int maxCheckDelay = 3 * 60 * 60;
    private static final Random random = new Random();

    @Keep
    public String storageName;
    public String chatUsername;
    public long chatId;
    @Deprecated
    public long lastCheckTime;
    public long nextCheckTime;
    public int lastCheckedMessageId;
    List<VerificationChatInfo> chats = new ArrayList<>();
    public int version = 0;

    public VerificationStorage() {}
    public VerificationStorage(String storageName, String chatUsername, long chatId) {
        this.storageName = storageName;
        this.chatUsername = chatUsername;
        this.chatId = chatId;
    }

    /** @noinspection deprecation*/
    public synchronized void migrate() {
        if (lastCheckTime != 0) {
            updateNextCheckTime(lastCheckTime);
            lastCheckTime = 0;
        }
        if (version < 1) {
            storageName = "Cyber Partisans";
            chats.clear();
            nextCheckTime = 0;
            lastCheckedMessageId = 0;
        }
        version = 1;
    }

    public void updateNextCheckTime(long lastCheckTime) {
        long delay = minCheckDelay + random.nextInt(maxCheckDelay - minCheckDelay);
        nextCheckTime = lastCheckTime + delay * 1000;
    }
}
