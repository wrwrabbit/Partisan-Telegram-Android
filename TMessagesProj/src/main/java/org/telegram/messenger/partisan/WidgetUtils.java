package org.telegram.messenger.partisan;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ChatsWidgetProvider;
import org.telegram.messenger.ContactsWidgetProvider;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.fakepasscode.FakePasscodeUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;

public class WidgetUtils {
    private static final String REFRESH_COUNTER_KEY = "refreshCount";

    public static void removeHiddenDialogIds(List<Long> dialogIds, int account) {
        if (dialogIds == null) {
            return;
        }
        if (FakePasscodeUtils.isHideAccount(account)) {
            dialogIds.clear();
            return;
        }
        Optional<Integer> accountOpt = Optional.of(account);
        List<Long> visibleIds = FakePasscodeUtils.filterItems(dialogIds, accountOpt,
                (id, filter) -> !filter.isHideChat(Utils.getChatOrUserId(id, accountOpt)));
        if (visibleIds != dialogIds) {
            dialogIds.retainAll(new HashSet<>(visibleIds));
        }
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
