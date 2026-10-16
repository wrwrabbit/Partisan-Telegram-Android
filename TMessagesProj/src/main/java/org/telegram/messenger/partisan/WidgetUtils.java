package org.telegram.messenger.partisan;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ChatsWidgetProvider;
import org.telegram.messenger.ContactsWidgetProvider;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.fakepasscode.FakePasscodeUtils;
import org.telegram.messenger.partisan.secretgroups.EncryptedGroupUtils;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class WidgetUtils {
    private static final String REFRESH_COUNTER_KEY = "refreshCount";

    public static boolean isDialogAllowedInWidget(long dialogId, int account) {
        return !isAccountHiddenInWidgets(account) && !isDialogHiddenInWidgets(dialogId, account);
    }

    public static void removeDialogIdsNotAllowedInWidget(List<Long> dialogIds, int account) {
        if (dialogIds == null) {
            return;
        }
        if (isAccountHiddenInWidgets(account)) {
            dialogIds.clear();
            return;
        }
        dialogIds.removeIf(id -> isDialogHiddenInWidgets(id, account));
    }

    public static ArrayList<TLRPC.Dialog> filterDialogsAllowedInWidget(List<TLRPC.Dialog> dialogs, int account) {
        if (isAccountHiddenInWidgets(account)) {
            return new ArrayList<>();
        }
        return dialogs.stream()
                .filter(dialog -> !isDialogHiddenInWidgets(dialog.id, account))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public static List<TLRPC.TL_topPeer> filterHintsAllowedInWidget(List<TLRPC.TL_topPeer> hints, int account) {
        if (isAccountHiddenInWidgets(account)) {
            return new ArrayList<>();
        }
        return hints.stream()
                .filter(hint -> !isDialogHiddenInWidgets(hint.peer.user_id, account))
                .collect(Collectors.toList());
    }

    private static boolean isAccountHiddenInWidgets(int account) {
        return FakePasscodeUtils.isHideAccount(account)
                || FakePasscodeUtils.isLogOutOrHideAccountInAnyFakePasscode(account);
    }

    // Widgets are visible before any passcode is entered, so they skip everything any fake passcode would hide or delete.
    private static boolean isDialogHiddenInWidgets(long dialogId, int account) {
        long chatOrUserId = Utils.getChatOrUserId(dialogId, Optional.of(account));
        return FakePasscodeUtils.isHideChat(chatOrUserId, account)
                || FakePasscodeUtils.isHideOrRemoveDialogInAnyFakePasscode(dialogId, account)
                || FakePasscodeUtils.isHideOrRemoveDialogInAnyFakePasscode(chatOrUserId, account)
                || isEncryptedGroupOrInnerChat(dialogId, account)
                || isLeftChannel(dialogId, account);
    }

    private static boolean isEncryptedGroupOrInnerChat(long dialogId, int account) {
        if (!DialogObject.isEncryptedDialog(dialogId)) {
            return false;
        }
        EncryptedGroupUtils encryptedGroupUtils = new EncryptedGroupUtils(account);
        encryptedGroupUtils.cacheEncryptedGroupBlockingIfNeeded(dialogId);
        return MessagesController.getInstance(account).isEncryptedGroup(dialogId)
                || encryptedGroupUtils.isInnerEncryptedGroupChat(dialogId);
    }

    private static boolean isLeftChannel(long dialogId, int account) {
        if (!DialogObject.isChatDialog(dialogId)) {
            return false;
        }
        MessagesController controller = MessagesController.getInstance(account);
        TLRPC.Chat chat = controller.getChat(-dialogId);
        if (chat == null) {
            chat = MessagesStorage.getInstance(account).getChatSync(-dialogId);
        }
        return chat != null
                && ChatObject.isChannel(chat)
                && chat.left
                && !controller.isPromoDialog(dialogId, false);
    }

    // The launcher keeps its cached rows while the service intent stays filterEquals, and hiding a chat
    // doesn't always change the row count. The extra makes the encoded data uri differ instead.
    // The intent ends up in the data uri, which leaves the app sandbox, so the extra must not hint at
    // a fake passcode: neither its name nor its value.
    public static void addRefreshCounterExtra(Intent widgetServiceIntent) {
        widgetServiceIntent.putExtra("refreshCount", getWidgetPreferences().getInt(REFRESH_COUNTER_KEY, 0));
    }

    private static void increaseRefreshCounter() {
        SharedPreferences preferences = getWidgetPreferences();
        preferences.edit()
                .putInt(REFRESH_COUNTER_KEY, preferences.getInt(REFRESH_COUNTER_KEY, 0) + 1)
                .commit();
    }

    private static SharedPreferences getWidgetPreferences() {
        return ApplicationLoader.applicationContext.getSharedPreferences("shortcut_widget", Context.MODE_PRIVATE);
    }

    public static void updateAllWidgets() {
        Utilities.globalQueue.postRunnable(() -> {
            increaseRefreshCounter();
            Context context = ApplicationLoader.applicationContext;
            AppWidgetManager manager = AppWidgetManager.getInstance(context);
            for (int widgetId : manager.getAppWidgetIds(new ComponentName(context, ChatsWidgetProvider.class))) {
                ChatsWidgetProvider.updateWidget(context, manager, widgetId);
            }
            for (int widgetId : manager.getAppWidgetIds(new ComponentName(context, ContactsWidgetProvider.class))) {
                ContactsWidgetProvider.updateWidget(context, manager, widgetId);
            }
        });
    }
}
