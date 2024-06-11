package org.telegram.messenger.partisan;

import static java.util.regex.Pattern.CASE_INSENSITIVE;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Environment;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;

import androidx.core.content.ContextCompat;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.DownloadController;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.fakepasscode.FakePasscodeUtils;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.CacheControlActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Utils {
    private static final Pattern FOREIGN_AGENT_REGEX = Pattern.compile("\\s*данное\\s*сообщение\\s*\\(материал\\)\\s*создано\\s*и\\s*\\(или\\)\\s*распространено\\s*(иностранным\\s*)?средством\\s*массовой\\s*информации,\\s*выполняющим\\s*функции\\s*иностранного\\s*агента,\\s*и\\s*\\(или\\)\\s*российским\\s*юридическим\\s*лицом,\\s*выполняющим\\s*функции\\s*иностранного\\s*агента[\\.\\s\\r\\n]*", CASE_INSENSITIVE);
    private static final Pattern FOREIGN_AGENT_REGEX2 = Pattern.compile("(\\s*18\\+)?\\s*настоящий\\s*материал\\s*(\\(информация\\))?\\s*произвед[её]н\\s*(,|и\\s*(\\(или\\))?)\\s*распространен(\\s*и\\s*\\(или\\)\\s*направлен)?\\s*иностранным\\s*агентом\\s*.*(\\s*либо\\s*касается\\s*деятельности\\s*иностранного\\s*агента\\s*.*)?(\\s*18\\+)?[\\.\\s\\r\\n]*", CASE_INSENSITIVE);
    private static final Pattern FOREIGN_AGENT_REGEX_WWF = Pattern.compile("\\s*настоящий\\s*материал\\s*(\\(информация\\))?\\s*произвед[её]н(,|\\s*и)\\s*распространен\\s*[\\w\\s]+,\\s*внесенным\\s*в\\s*реестр\\s*иностранных\\s*агентов,\\s*либо\\s*касается\\s*деятельности\\s*[\\w\\s]+,\\s*внесенного\\s*в\\s*реестр\\s*иностранных\\s*агентов.[\\.\\s\\r\\n]*", CASE_INSENSITIVE);
    static Location getLastLocation() {
        boolean permissionGranted = ContextCompat.checkSelfPermission(ApplicationLoader.applicationContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (!permissionGranted) {
            return null;
        }

        LocationManager lm = (LocationManager) ApplicationLoader.applicationContext.getSystemService(Context.LOCATION_SERVICE);
        List<String> providers = lm.getProviders(true);
        Location l = null;
        for (int i = providers.size() - 1; i >= 0; i--) {
            l = lm.getLastKnownLocation(providers.get(i));
            if (l != null) {
                break;
            }
        }
        return l;
    }

    public static String getLastLocationString() {
        Location loc = Utils.getLastLocation();
        if (loc != null) {
            return " " + LocaleController.getString("Geolocation", R.string.Geolocation) + ":" + loc.getLatitude() + ", " + loc.getLongitude();
        } else {
            return "";
        }
    }

    public static void clearCache(Runnable callback) {
        Utilities.globalQueue.postRunnable(() -> {
            for (int a = 0; a < 8; a++) {
                int type = -1;
                int documentsMusicType = 0;
                if (a == 0) {
                    type = FileLoader.MEDIA_DIR_IMAGE;
                } else if (a == 1) {
                    type = FileLoader.MEDIA_DIR_VIDEO;
                } else if (a == 2) {
                    type = FileLoader.MEDIA_DIR_DOCUMENT;
                    documentsMusicType = 1;
                } else if (a == 3) {
                    type = FileLoader.MEDIA_DIR_DOCUMENT;
                    documentsMusicType = 2;
                } else if (a == 4) {
                    type = FileLoader.MEDIA_DIR_AUDIO;
                } else if (a == 5) {
                    type = FileLoader.MEDIA_DIR_STORIES;
                } else if (a == 6) {
                    type = 100;
                } else if (a == 7) {
                    type = FileLoader.MEDIA_DIR_CACHE;
                }
                if (type == -1) {
                    continue;
                }
                File file;
                if (type == 100) {
                    file = new File(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_CACHE), "acache");
                } else {
                    file = FileLoader.checkDirectory(type);
                }
                if (file != null) {
                    CacheControlActivity.cleanDirJava(file.getAbsolutePath(), documentsMusicType, null, x -> {});
                }

                if (type == 100) {
                    File drafts = new File(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_CACHE), "drafts");
                    CacheControlActivity.cleanDirJava(drafts.getAbsolutePath(), documentsMusicType, null, x -> {});
                }

                if (type == FileLoader.MEDIA_DIR_IMAGE || type == FileLoader.MEDIA_DIR_VIDEO) {
                    int publicDirectoryType;
                    if (type == FileLoader.MEDIA_DIR_IMAGE) {
                        publicDirectoryType = FileLoader.MEDIA_DIR_IMAGE_PUBLIC;
                    } else {
                        publicDirectoryType = FileLoader.MEDIA_DIR_VIDEO_PUBLIC;
                    }
                    file = FileLoader.checkDirectory(publicDirectoryType);

                    if (file != null) {
                        CacheControlActivity.cleanDirJava(file.getAbsolutePath(), documentsMusicType, null, x -> {});
                    }
                }
                if (type == FileLoader.MEDIA_DIR_DOCUMENT) {
                    file = FileLoader.checkDirectory(FileLoader.MEDIA_DIR_FILES);
                    if (file != null) {
                        CacheControlActivity.cleanDirJava(file.getAbsolutePath(), documentsMusicType, null, x -> {});
                    }
                }

                file = new File(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_CACHE), "sharing");
                CacheControlActivity.cleanDirJava(file.getAbsolutePath(), 0, null, x -> {});

                File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                File logs = new File(downloads, "logs");
                if (logs.exists()) {
                    try {
                        CacheControlActivity.cleanDirJava(logs.getAbsolutePath(), 0, null, x -> {});
                        logs.delete();
                    } catch (Exception ignore) {
                    }
                }

                logs = new File(ApplicationLoader.applicationContext.getExternalFilesDir(null), "logs");
                if (logs.exists()) {
                    CacheControlActivity.cleanDirJava(logs.getAbsolutePath(), 0, null, x -> {});
                    logs.delete();
                }
            }

            AndroidUtilities.runOnUIThread(() -> {
                for (int i = UserConfig.MAX_ACCOUNT_COUNT - 1; i >= 0; i--) {
                    if (UserConfig.getInstance(i).isClientActivated()) {
                        DownloadController controller = DownloadController.getInstance(i);
                        controller.deleteRecentFiles(new ArrayList<>(controller.recentDownloadingFiles));
                        controller.deleteRecentFiles(new ArrayList<>(controller.downloadingFiles));
                    }
                }
                if (callback != null) {
                    try {
                        callback.run();
                    } catch (Exception ignored) {
                    }
                }
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.cacheClearedByPtg);
            });
        });
    }

    public static void deleteDialog(int accountNum, long id) {
        deleteDialog(accountNum, id, false);
    }

    public static void deleteDialog(int accountNum, long id, boolean revoke) {
        AccountInstance account = AccountInstance.getInstance(accountNum);
        MessagesController messagesController = account.getMessagesController();
        TLRPC.Chat chat = null;
        TLRPC.User user = null;
        if (id > 0) {
            user = messagesController.getUser(id);
        } else {
            chat = messagesController.getChat(-id);
        }
        if (chat != null) {
            if (ChatObject.isNotInChat(chat)) {
                messagesController.deleteDialog(id, 0, revoke);
            } else {
                TLRPC.User currentUser = messagesController.getUser(account.getUserConfig().getClientUserId());
                messagesController.deleteParticipantFromChat(-id, currentUser);
            }
        } else {
            messagesController.deleteDialog(id, 0, revoke);
            MediaDataController.getInstance(accountNum).removePeer(id);
        }
        Utilities.globalQueue.postRunnable(() -> {
            if (isDialogsLeft(accountNum, new HashSet<>(Arrays.asList(id)))) {
                AndroidUtilities.runOnUIThread(() -> Utils.deleteDialog(accountNum, id, revoke));
            }
        }, 1000);
    }

    public static boolean isDialogsLeft(int accountNum, Set<Long> ids) {
        ArrayList<TLRPC.Dialog> dialogs = AccountInstance.getInstance(accountNum)
                .getMessagesController()
                .getDialogs(0);
        return new ArrayList<>(dialogs) // Workaround for ConcurrentModificationException
                .stream()
                .anyMatch(e -> ids.contains(e.id));
    }

    public static long getChatOrUserId(long id, Optional<Integer> account) {
        if (!DialogObject.isEncryptedDialog(id) || !account.isPresent()) {
            return id;
        } else {
            MessagesController controller = MessagesController.getInstance(account.get());
            TLRPC.EncryptedChat encryptedChat = controller.getEncryptedChat((int) (id >> 32));
            if (encryptedChat != null) {
                return encryptedChat.user_id;
            } else {
                return id;
            }
        }
    }

    public static boolean isNetworkConnected() {
        for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; i++) {
            AccountInstance account = AccountInstance.getInstance(i);
            ConnectionsManager connectionsManager = account.getConnectionsManager();
            int connectionState = connectionsManager.getConnectionState();
            if (connectionState != ConnectionsManager.ConnectionStateWaitingForNetwork) {
                return true;
            }
        }
        return false;
    }

    public static String fixStringMessage(String message) {
        return fixStringMessage(message, false);
    }

    public static String fixStringMessage(String message, boolean leaveEmpty) {
        if (message == null) {
            return null;
        }
        CharSequence fixedMessage = fixMessage(message, leaveEmpty);
        if (fixedMessage == null) {
            return null;
        }
        return fixedMessage.toString();
    }

    public static void fixTlrpcMessage(TLRPC.Message message) {
        if (message == null) {
            return;
        }
        if (SharedConfig.cutForeignAgentsText && !FakePasscodeUtils.isFakePasscodeActivated()) {
            try {
                SpannableString source = new SpannableString(message.message);
                for (TLRPC.MessageEntity entity : message.entities) {
                    source.setSpan(entity, entity.offset, entity.offset + entity.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                CharSequence result = cutForeignAgentPart(source, message.media != null);
                message.message = result.toString();
                if (result instanceof Spannable) {
                    Spannable spannable = (Spannable) result;
                    TLRPC.MessageEntity[] entities = spannable.getSpans(0, result.length(), TLRPC.MessageEntity.class);
                    for (TLRPC.MessageEntity entity : entities) {
                        entity.offset = spannable.getSpanStart(entity);
                        entity.length = spannable.getSpanEnd(entity) - entity.offset;
                    }
                    message.entities.clear();
                    message.entities.addAll(Arrays.asList(entities));
                }
            } catch (Exception e) {
                message.message = fixStringMessage(message.message, message.media != null);
            }

        }
    }

    public static CharSequence fixMessage(CharSequence message) {
        return fixMessage(message, false);
    }

    public static CharSequence fixMessage(CharSequence message, boolean leaveEmpty) {
        if (message == null) {
            return null;
        }
        CharSequence fixedMessage = message;
        if (SharedConfig.cutForeignAgentsText && !FakePasscodeUtils.isFakePasscodeActivated()) {
            fixedMessage = cutForeignAgentPart(message, leaveEmpty);
        }
        return fixedMessage;
    }

    private static CharSequence cutForeignAgentPart(CharSequence message, boolean leaveEmpty) {
        int lastEnd = -1;
        SpannableStringBuilder builder = new SpannableStringBuilder();
        for (Pattern regex : Arrays.asList(FOREIGN_AGENT_REGEX, FOREIGN_AGENT_REGEX2, FOREIGN_AGENT_REGEX_WWF)) {
            Matcher matcher = regex.matcher(message);
            while (matcher.find()) {
                if (lastEnd == -1) {
                    builder.append(message.subSequence(0, matcher.start()));
                } else {
                    builder.append(message.subSequence(lastEnd, matcher.start()));
                }
                lastEnd = matcher.end();
            }
        }
        if (lastEnd != -1) {
            CharSequence endCharSequence = message.subSequence(lastEnd, message.length());
            if (builder.length() != 0 && endCharSequence.length() != 0) {
                builder.append("\n\n");
                builder.append(endCharSequence);
            }
            if (builder.length() != 0) {
                int end = builder.length() - 1;
                while (end > 0 && Character.isWhitespace(builder.charAt(end))) {
                    end--;
                }
                if (end != builder.length() - 1) {
                    builder.replace(end, builder.length(), "");
                }
                return SpannableString.valueOf(builder);
            } else {
                return leaveEmpty ? "" : message;
            }
        } else {
            CharSequence fixed = message;
            fixed = cutTrimmedForeignAgentPart(fixed, leaveEmpty,
                    "данное сообщение (материал) создано и (или) распространено",
                    Collections.singletonList(FOREIGN_AGENT_REGEX));
            fixed = cutTrimmedForeignAgentPart(fixed, leaveEmpty,
                    "настоящий материал (информация)",
                    Collections.singletonList(FOREIGN_AGENT_REGEX2));
            return fixed;
        }
    }

    private static CharSequence cutTrimmedForeignAgentPart(CharSequence message, boolean leaveEmpty, String foreignAgentPartStart, List<Pattern> regexes) {
        String lowerCased = message.toString().toLowerCase(Locale.ROOT);
        int startIndex = lowerCased.indexOf(foreignAgentPartStart);
        if (startIndex != -1) {
            int endIndex = lowerCased.length();
            while (endIndex > 0 && lowerCased.charAt(endIndex - 1) == '.' || lowerCased.charAt(endIndex - 1) == '…') {
                endIndex--;
            }
            if (endIndex - startIndex < 50) {
                return message;
            }
            CharSequence endPart = lowerCased.subSequence(startIndex, endIndex);
            boolean matches = false;
            for (Pattern regex : regexes) {
                Matcher matcher = regex.matcher(endPart);
                if (!matcher.matches() && matcher.hitEnd()) {
                    matches = true;
                    break;
                }
            }
            if (matches) {
                while (startIndex > 0 && Character.isWhitespace(message.charAt(startIndex - 1))) {
                    startIndex--;
                }
                if (startIndex > 0) {
                    return message.subSequence(0, startIndex);
                } else {
                    return leaveEmpty ? "" : message;
                }
            }
        }
        return message;
    }

    public static void clearAllDrafts() {
        clearDrafts(null);
    }

    public static void clearDrafts(Integer acc) {
        TLRPC.TL_messages_clearAllDrafts req = new TLRPC.TL_messages_clearAllDrafts();
        for (int i = UserConfig.MAX_ACCOUNT_COUNT - 1; i >= 0; i--) {
            if (UserConfig.getInstance(i).isClientActivated() && (acc == null || acc == i)) {
                final int accountNum = i;
                ConnectionsManager.getInstance(accountNum).sendRequest(req, null);
                runOnUIThreadAsSoonAsPossible(() -> MediaDataController.getInstance(accountNum).clearAllDrafts(true));
            }
        }
    }

    public static boolean loadAllDialogs(int accountNum) {
        MessagesController controller = AccountInstance.getInstance(accountNum).getMessagesController();
        boolean loadFromCache = !controller.isDialogsEndReached(0);
        boolean load = loadFromCache || !controller.isServerDialogsEndReached(0);
        boolean loadArchivedFromCache = !controller.isDialogsEndReached(1);
        boolean loadArchived = loadArchivedFromCache || !controller.isServerDialogsEndReached(1);
        if (load || loadArchived) {
            AndroidUtilities.runOnUIThread(() -> {
                if (load) {
                    controller.loadDialogs(0, -1, 100, loadFromCache);
                }
                if (loadArchived) {
                    controller.loadDialogs(1, -1, 100, loadFromCache);
                }
            });
        }
        return load || loadArchived;
    }

    public static List<TLRPC.Dialog> getAllDialogs(int accountNum) {
        MessagesController controller = AccountInstance.getInstance(accountNum).getMessagesController();
        return Stream.concat(controller.getDialogs(0).stream(), controller.getDialogs(1).stream())
                .filter(d -> !(d instanceof TLRPC.TL_dialogFolder))
                .collect(Collectors.toList());
    }

    public static void clearDownloads() {
        Utilities.globalQueue.postRunnable(() -> {
            deleteDirectory(new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Telegram"));
            deleteDirectory(new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "Telegram"));
            deleteDirectory(new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "Telegram"));
            deleteDirectory(new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Telegram"));
        });
    }

    private static boolean deleteDirectory(File path) {
        if (path.exists()) {
            File[] files = path.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
        }
        return path.delete();
    }

    public static String removeUsernamePrefixed(String username) {
        return username.replace("@", "")
                .replace("https://t.me/", "")
                .replace("http://t.me/", "")
                .replace("t.me/", "");
    }

    public static void updateMessagesPreview() {
        androidx.collection.LongSparseArray<ArrayList<MessageObject>> dialogMessages
                = MessagesController.getInstance(UserConfig.selectedAccount).dialogMessage;
        for (int i = 0; i < dialogMessages.size(); i++) {
            if (dialogMessages.valueAt(i) == null) {
                continue;
            }
            for (MessageObject message : dialogMessages.valueAt(i)) {
                message.fakePasscodeUpdateMessageText();
            }
        }
    }

    public static void runOnUIThreadAsSoonAsPossible(Runnable runnable) {
        if (Thread.currentThread() == ApplicationLoader.applicationHandler.getLooper().getThread()) {
            runnable.run();
        } else {
            ApplicationLoader.applicationHandler.postAtFrontOfQueue(runnable);
        }
    }

    public static byte[] concatByteArrays(byte[] first, byte[] second) {
        final byte[] combined = new byte[first.length + second.length];
        System.arraycopy(first, 0, combined, 0, first.length);
        System.arraycopy(second, 0, combined, first.length, second.length);
        return combined;
    }
}
