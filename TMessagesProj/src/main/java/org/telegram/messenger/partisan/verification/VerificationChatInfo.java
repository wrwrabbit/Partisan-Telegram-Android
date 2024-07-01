package org.telegram.messenger.partisan.verification;

import androidx.annotation.Keep;

public class VerificationChatInfo {
    public long chatId;
    @Keep
    public String username;
    public int type;

    public VerificationChatInfo(long chatId, String username, int type) {
        this.chatId = chatId;
        this.username = username;
        this.type = type;
    }

    public VerificationChatInfo() {}
}
