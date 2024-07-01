package org.telegram.messenger.partisan.verification;

import android.content.Context;
import android.content.SharedPreferences;

import com.fasterxml.jackson.core.JsonProcessingException;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.fakepasscode.FakePasscodeUtils;
import org.telegram.messenger.partisan.PartisanLog;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class VerificationRepository {
    public static final int TYPE_VERIFIED = 1;
    public static final int TYPE_SCAM = 2;
    public static final int TYPE_FAKE = 3;
    private boolean repositoryLoaded;
    private boolean loadedWithErrors;
    private List<VerificationStorage> storages = new ArrayList<>();
    private final Map<Long, Integer> cacheTypeByChatId = new HashMap<>();
    private static VerificationRepository instance;

    public static synchronized VerificationRepository getInstance() {
        if (instance == null) {
            instance = new VerificationRepository();
        }
        return instance;
    }

    private static class StoragesWrapper {
        public List<VerificationStorage> verificationStorages;
        public StoragesWrapper(List<VerificationStorage> verificationStorages) {
            this.verificationStorages = verificationStorages;
        }
        public StoragesWrapper() {}
    }

    public synchronized void loadRepository() {
        try {
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("verified", Context.MODE_PRIVATE);
            boolean repositoryFilled = preferences.contains("storages");
            if (repositoryFilled) {
                storages = SharedConfig.fromJson(preferences.getString("storages", null), StoragesWrapper.class).verificationStorages;
                for (VerificationStorage storage : storages) {
                    storage.migrate();
                }
                updateCache();
            } else {
                fillRepository();
            }
        } catch (Exception e) {
            PartisanLog.e("VerificationRepository", e);
            loadedWithErrors = true;
        }
    }

    public void saveRepository() {
        if (!loadedWithErrors) {
            try {
                SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("verified", Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = preferences.edit();
                editor.putString("storages", SharedConfig.toJson(new StoragesWrapper(storages)));
                editor.apply();
                updateCache();
            } catch (JsonProcessingException ignore) {
            }
        }
    }

    private void fillRepository() {
        try {
            VerificationStorage storage;
            if (BuildVars.isAlphaApp()) {
                storage = new VerificationStorage("Cyber Partisans Test", "ptg_verification_alpha", -2013847836);
            } else {
                storage = new VerificationStorage("Cyber Partisans", "ptgsymb", -2064662503);
            }
            storages.add(storage);
            saveRepository();
        } catch (Exception ignore) {
        }
    }

    private void updateCache() {
        cacheTypeByChatId.clear();

        for (VerificationStorage storage : storages) {
            for (VerificationChatInfo chat : storage.chats) {
                cacheTypeByChatId.put(chat.chatId, chat.type);
            }
        }
    }

    private int getChatType(long chatId) {
        ensureRepositoryLoaded();
        Integer type = cacheTypeByChatId.get(chatId);
        return type != null ? type : -1;
    }

    private boolean checkChatType(long chatId, int targetType, boolean targetValue) {
        if (FakePasscodeUtils.isFakePasscodeActivated() || !SharedConfig.additionalVerifiedBadges) {
            return targetValue;
        }
        int type = getChatType(chatId);
        if (type != -1) {
            return type == targetType;
        } else {
            return targetValue;
        }
    }

    public boolean isVerified(long chatId, boolean verified) {
        return checkChatType(chatId, TYPE_VERIFIED, verified);
    }

    public boolean isScam(long chatId, boolean scam) {
        return checkChatType(chatId, TYPE_SCAM, scam);
    }

    public boolean isFake(long chatId, boolean fake) {
        return checkChatType(chatId, TYPE_FAKE, fake);
    }

    public List<VerificationStorage> getStorages() {
        ensureRepositoryLoaded();
        return storages;
    }

    public VerificationStorage getStorage(long chatId) {
        ensureRepositoryLoaded();
        return storages.stream()
                .filter(s -> s.chatId == chatId)
                .findAny()
                .orElse(null);
    }

    public VerificationStorage getStorage(TLRPC.InputPeer peer) {
        ensureRepositoryLoaded();
        return VerificationRepository.getInstance().getStorages().stream()
                .filter(s -> s.chatId == peer.channel_id
                        || s.chatId == -peer.channel_id
                        || s.chatId == peer.chat_id
                        || s.chatId == -peer.chat_id)
                .findAny()
                .orElse(null);
    }

    public void deleteStorage(long chatId) {
        ensureRepositoryLoaded();
        storages.removeIf(s -> s.chatId == chatId);
        saveRepository();
    }

    public void addStorage(String name, String username, long chatId) {
        ensureRepositoryLoaded();
        storages.add(new VerificationStorage(name, username, chatId));
        saveRepository();
    }

    public void saveNextCheckTime(long storageChatId, long lastCheckTime) {
        ensureRepositoryLoaded();
        storages.stream()
                .filter(s -> s.chatId == storageChatId)
                .forEach(s -> s.updateNextCheckTime(lastCheckTime));
        saveRepository();
    }

    public void saveLastCheckedMessageId(long storageChatId, int lastCheckedMessageId) {
        ensureRepositoryLoaded();
        storages.stream()
                .filter(s -> s.chatId == storageChatId)
                .forEach(s -> s.lastCheckedMessageId = lastCheckedMessageId);
        saveRepository();
    }

    public void saveRepositoryChatUsername(long storageChatId, String username) {
        ensureRepositoryLoaded();
        storages.stream()
                .filter(s -> s.chatId == storageChatId)
                .forEach(s -> {
                    if (!s.chatUsername.equals(username)) {
                        s.chatUsername = username;
                        s.chatId = 0;
                        s.lastCheckedMessageId = 0;
                    }
                });
        saveRepository();
    }

    public void saveRepositoryChatId(String storageChatUsername, long chatId) {
        ensureRepositoryLoaded();
        storages.stream()
                .filter(s -> s.chatUsername.equals(storageChatUsername))
                .forEach(s -> s.chatId = chatId);
        saveRepository();
    }

    public void putChats(long storageChatId, List<VerificationChatInfo> chats) {
        ensureRepositoryLoaded();
        VerificationStorage storage = storages.stream()
                .filter(s -> s.chatId == storageChatId)
                .findAny()
                .orElse(null);
        if (storage == null) {
            return;
        }
        Set<Long> existedIds = storage.chats.stream().map(c -> c.chatId).collect(Collectors.toSet());
        for (VerificationChatInfo chat : chats) {
            if (existedIds.contains(chat.chatId)) {
                storage.chats.removeIf(c -> c.chatId == chat.chatId);
            }
            storage.chats.add(chat);
        }
        saveRepository();
    }

    public void deleteChats(long storageChatId, List<VerificationChatInfo> chats) {
        ensureRepositoryLoaded();
        storages.stream()
                .filter(s -> s.chatId == storageChatId)
                .forEach(s -> s.chats.removeIf(c ->
                        chats.stream().anyMatch(toDelete -> c.type == toDelete.type && c.chatId == toDelete.chatId))
                );
        saveRepository();
    }

    public void ensureRepositoryLoaded() {
        if (!repositoryLoaded) {
            loadRepository();
            repositoryLoaded = true;
        }
    }
}
