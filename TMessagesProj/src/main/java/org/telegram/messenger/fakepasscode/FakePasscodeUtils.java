package org.telegram.messenger.fakepasscode;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.text.TextUtils;

import org.telegram.messenger.AppStartReceiver;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationsController;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.fakepasscode.results.ActionsResult;
import org.telegram.messenger.fakepasscode.results.RemoveChatsResult;
import org.telegram.messenger.fakepasscode.results.TelegramMessageResult;
import org.telegram.messenger.partisan.Utils;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stories;
import org.telegram.ui.NotificationsSettingsActivity;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

public class FakePasscodeUtils {
    public static FakePasscode getActivatedFakePasscode() {
        if (SharedConfig.fakePasscodeActivatedIndex > -1
                && SharedConfig.fakePasscodeActivatedIndex < SharedConfig.fakePasscodes.size()) {
            FakePasscode fakePasscode = SharedConfig.fakePasscodes.get(SharedConfig.fakePasscodeActivatedIndex);
            if (fakePasscode.activated) {
                return fakePasscode;
            }
        }
        return null;
    }

    public static boolean isFakePasscodeActivated() {
        return getActivatedFakePasscode() != null;
    }

    private static ActionsResult getActivatedActionsResult() {
        if (isFakePasscodeActivated()) {
            return getActivatedFakePasscode().actionsResult.merge(SharedConfig.fakePasscodeActionsResult);
        } else {
            return SharedConfig.fakePasscodeActionsResult;
        }
    }

    public static ActionsResult getJustActivatedActionsResult() {
        ActionsResult result = getActivatedActionsResult();
        if (result != null && result.isJustActivated()) {
            return result;
        }
        return null;
    }

    public static RemoveChatsResult getJustActivatedRemoveChatsResult(int accountNum) {
        ActionsResult result = getActivatedActionsResult();
        if (result != null && result.isJustActivated()) {
            return result.removeChatsResults.get(accountNum);
        }
        return null;
    }

    public static String getFakePhoneNumber(int accountNum) {
        ActionsResult actionsResult = getActivatedActionsResult();
        if (actionsResult != null) {
            String number = actionsResult.getFakePhoneNumber(accountNum);
            if (number != null) {
                return number;
            }
        }
        return SharedConfig.phoneOverride;
    }

    public static String getFakePhoneNumber(int accountNum, String fallback) {
        String fakeNumber = getFakePhoneNumber(accountNum);
        if (TextUtils.isEmpty(fakeNumber)) {
            return fallback;
        } else {
            return fakeNumber;
        }
    }

    public static <T> List<T> filterItems(List<T> items, Optional<Integer> account, BiPredicate<T, ChatFilter> filter) {
        FakePasscode passcode = getActivatedFakePasscode();
        ActionsResult actionsResult = getActivatedActionsResult();
        if ((passcode == null && actionsResult == null) || items == null || (passcode != null && !passcode.activated)) {
            return items;
        }
        List<T> filteredItems = items;
        for (Map.Entry<Integer, RemoveChatsResult> pair : actionsResult.removeChatsResults.entrySet()) {
            Integer accountNum = pair.getKey();
            if (accountNum != null && (!account.isPresent() || accountNum.equals(account.get()))) {
                filteredItems = filteredItems.stream().filter(i -> filter.test(i, pair.getValue())).collect(Collectors.toList());
            }
        }
        if (passcode != null) {
            for (AccountActions actions : passcode.getFilteredAccountActions()) {
                Integer accountNum = actions.getAccountNum();
                if (accountNum != null && (!account.isPresent() ||  accountNum.equals(account.get()))) {
                    filteredItems = filteredItems.stream().filter(i -> filter.test(i, actions.getRemoveChatsAction())).collect(Collectors.toList());
                }
            }
        }
        return new FilteredArrayList<>(filteredItems, items);
    }

    public static List<TLRPC.Dialog> filterDialogs(List<TLRPC.Dialog> dialogs, Optional<Integer> account) {
        return filterItems(dialogs, account, (dialog, filter) -> !filter.isHideChat(Utils.getChatOrUserId(dialog.id, account)));
    }

    public static List<TLRPC.TL_topPeer> filterHints(List<TLRPC.TL_topPeer> hints, int account) {
        return filterItems(hints, Optional.of(account), (peer, filter) ->
                !filter.isHideChat(peer.peer.chat_id)
                        && !filter.isHideChat(peer.peer.channel_id)
                        && !filter.isHideChat(peer.peer.user_id));
    }

    public static List<TLRPC.Peer> filterPeers(List<TLRPC.Peer> peers, int account) {
        return filterItems(peers, Optional.of(account), (peer, filter) -> !filter.isHidePeer(peer));
    }

    public static List<TLRPC.TL_sendAsPeer> filterSendAsPeers(List<TLRPC.TL_sendAsPeer> peers, int account) {
        return filterItems(peers, Optional.of(account), (peer, filter) -> !filter.isHidePeer(peer.peer));
    }

    public static boolean isHidePeer(TLRPC.Peer peer, int account) {
        FakePasscode passcode = getActivatedFakePasscode();
        ActionsResult actionsResult = getActivatedActionsResult();
        if ((passcode == null && actionsResult == null) || peer == null) {
            return false;
        }
        RemoveChatsResult result = actionsResult.getRemoveChatsResult(account);
        if (result != null && result.isHidePeer(peer)) {
            return true;
        }
        if (passcode != null) {
            for (AccountActions actions : passcode.getFilteredAccountActions()) {
                Integer accountNum = actions.getAccountNum();
                if (accountNum != null && accountNum.equals(account)) {
                    return actions.getRemoveChatsAction().isHidePeer(peer);
                }
            }
        }
        return false;
    }

    public static List<TLRPC.TL_contact> filterContacts(List<TLRPC.TL_contact> contacts, int account) {
        return filterItems(contacts, Optional.of(account), (contact, filter) -> !filter.isHideChat(contact.user_id));
    }

    public static List<Long> filterDialogIds(List<Long> ids, int account) {
        return filterItems(ids, Optional.of(account), (id, filter) -> !filter.isHideChat(id));
    }

    public static List<MessagesController.DialogFilter> filterFolders(List<MessagesController.DialogFilter> folders, int account) {
        return filterItems(folders, Optional.of(account), (folder, filter) -> !filter.isHideFolder(folder.id));
    }

    public static List<NotificationsSettingsActivity.NotificationException> filterNotificationExceptions(
            List<NotificationsSettingsActivity.NotificationException> exceptions, int account) {
        if (exceptions == null) {
            return null;
        }
        return filterItems(exceptions, Optional.of(account), (e, filter) -> !filter.isHideChat(e.did));
    }

    public static List<TL_stories.PeerStories> filterStories(List<TL_stories.PeerStories> stories, int account) {
        if (stories == null) {
            return null;
        }
        return filterItems(stories, Optional.of(account), (s, filter) -> !filter.isHidePeer(s.peer));
    }

    public static boolean isHideChat(long chatId, int account) {
        FakePasscode passcode = getActivatedFakePasscode();
        ActionsResult actionsResult = getActivatedActionsResult();
        if (passcode == null && actionsResult == null) {
            return false;
        }
        RemoveChatsResult result = actionsResult.getRemoveChatsResult(account);
        if (result != null && result.isHideChat(chatId)) {
            return true;
        }
        if (passcode != null) {
            AccountActions actions = passcode.getAccountActions(account);
            return actions != null && actions.getRemoveChatsAction().isHideChat(chatId);
        } else {
            return false;
        }
    }

    public static boolean isHideFolder(int folderId, int account) {
        FakePasscode passcode = getActivatedFakePasscode();
        ActionsResult actionsResult = getActivatedActionsResult();
        if (passcode == null && actionsResult == null) {
            return false;
        }
        RemoveChatsResult result = actionsResult.getRemoveChatsResult(account);
        if (result != null && result.isHideFolder(folderId)) {
            return true;
        }
        if (passcode != null) {
            AccountActions actions = passcode.getAccountActions(account);
            return actions != null && actions.getRemoveChatsAction().isHideFolder(folderId);
        } else {
            return false;
        }
    }

    public static boolean isHideAccount(int account) {
        return isHideAccount(account, false);
    }

    public static boolean isHideAccount(int account, boolean strictHiding) {
        FakePasscode passcode = getActivatedFakePasscode();
        ActionsResult actionsResult = getActivatedActionsResult();
        if (passcode == null && actionsResult == null) {
            return false;
        }
        if (actionsResult.isHideAccount(account, strictHiding)) {
            return true;
        }
        if (passcode != null) {
            AccountActions actions = passcode.getAccountActions(account);
            return actions != null && actions.isHideAccount(strictHiding);
        } else {
            return false;
        }
    }

    public static boolean autoAddHidingsToAllFakePasscodes() {
        boolean result = false;
        for (FakePasscode fakePasscode: SharedConfig.fakePasscodes) {
            result |= fakePasscode.autoAddAccountHidings();
        }
        return result;
    }

    public static void cleanupHiddenAccountSystemNotifications() {
        Map<Integer, Boolean> hideMap = getLogoutOrHideAccountMap();
        for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; i++) {
            Boolean hidden = hideMap.get(i);
            if (hidden != null && hidden) {
                NotificationsController.getInstance(i).cleanupSystemSettings();
            }
        }
    }

    public static void checkPendingRemovalChats() {
        if (RemoveChatsAction.pendingRemovalChatsChecked) {
            return;
        }
        FakePasscode passcode = getActivatedFakePasscode();
        if (passcode != null) {
            for (AccountActions actions : passcode.accountActions) {
                actions.getRemoveChatsAction().checkPendingRemovalChats();
            }
        }
        RemoveChatsAction.pendingRemovalChatsChecked = true;
    }

    public static Map<Integer, Boolean> getLogoutOrHideAccountMap() {
        Map<Integer, Boolean> result = new HashMap<>();
        for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; i++) {
            result.put(i, UserConfig.getInstance(i).isClientActivated() ? false : null);
        }
        for (FakePasscode fakePasscode: SharedConfig.fakePasscodes) {
            for (AccountActions actions : fakePasscode.getFilteredAccountActions()) {
                if (actions.isLogOutOrHideAccount()) {
                    result.put(actions.getAccountNum(), true);
                }
            }
        }
        return result;
    }

    public static boolean isHideMessage(int accountNum, Long dialogId, Integer messageId) {
        return isHideMessage(accountNum, dialogId, messageId, false);
    }

    public static boolean isHideMessage(int accountNum, Long dialogId, Integer messageId, boolean strictHiding) {
        FakePasscode passcode = getActivatedFakePasscode();
        ActionsResult actionsResult = getActivatedActionsResult();
        if (passcode == null && actionsResult == null) {
            return false;
        }

        RemoveChatsResult removeChatsResult = actionsResult.getRemoveChatsResult(accountNum);
        if (removeChatsResult != null && removeChatsResult.isHideChat(dialogId, strictHiding)) {
            return true;
        }
        if (passcode != null) {
            AccountActions actions = passcode.getAccountActions(accountNum);
            if (actions != null) {
                RemoveChatsAction removeChatsAction = passcode.getAccountActions(accountNum).getRemoveChatsAction();
                boolean hideAccount = passcode.getAccountActions(accountNum).isHideAccount(strictHiding);
                if (hideAccount || removeChatsAction.isHideChat(dialogId, strictHiding)) {
                    return true;
                }
            }
        }

        if (messageId != null) {
            TelegramMessageResult telegramMessageResult = actionsResult.getTelegramMessageResult(accountNum);
            if (telegramMessageResult != null && telegramMessageResult.isSosMessage(dialogId, messageId)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isSosMessage(int accountNum, Long dialogId, int messageId) {
        ActionsResult actionsResult = getActivatedActionsResult();
        if (!isFakePasscodeActivated() && actionsResult == null) {
            return false;
        }

        TelegramMessageResult telegramMessageResult = actionsResult.getTelegramMessageResult(accountNum);
        return telegramMessageResult != null && telegramMessageResult.isSosMessage(dialogId, messageId);
    }

    public static FakePasscode getFingerprintFakePasscode() {
        for (FakePasscode passcode : SharedConfig.fakePasscodes) {
            if (passcode.activateByFingerprint) {
                return passcode;
            }
        }
        return null;
    }

    public static boolean isPreventStickersBulletin() {
        FakePasscode passcode = getActivatedFakePasscode();
        if (passcode == null) {
            return false;
        }
        return passcode.accountActions.stream().anyMatch(AccountActions::isPreventStickersBulletin);
    }

    public static boolean isNeedSwitchAccount() {
        if (!isFakePasscodeActivated()) {
            return false;
        }
        boolean isCurrentAccountCorrect = false;
        int accountIndex = 0;
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (UserConfig.getInstance(a).isClientActivated() && !FakePasscodeUtils.isHideAccount(a, false)) {
                if (a == UserConfig.selectedAccount) {
                    isCurrentAccountCorrect = true;
                    break;
                }
                accountIndex++;
                if (accountIndex >= UserConfig.getFakePasscodeMaxAccountCount()) {
                    break;
                }
            }
        }
        return !isCurrentAccountCorrect;
    }

    private static boolean isFakePasscodeWithTimerExist() {
        return SharedConfig.fakePasscodes.stream().anyMatch(f -> f.activateByTimerTime != null);
    }

    public static void updateLastPauseFakePasscodeTime() {
        if (SharedConfig.lastPauseFakePasscodeTime == 0 && isFakePasscodeWithTimerExist()) {
            SharedConfig.lastPauseFakePasscodeTime = SystemClock.elapsedRealtime() / 1000;
        }
    }

    private static int getActivatePasscodeTimerDuration() {
        FakePasscode activePasscode = getActivatedFakePasscode();
        return activePasscode != null && activePasscode.activateByTimerTime != null
                ? activePasscode.activateByTimerTime
                : 0;
    }

    public static synchronized void tryActivateByTimer() {
        try {
            if (SharedConfig.lastPauseFakePasscodeTime == 0) {
                return;
            }
            long uptime = SystemClock.elapsedRealtime() / 1000;
            long duration = uptime - SharedConfig.lastPauseFakePasscodeTime;
            List<FakePasscode> sortedPasscodes = SharedConfig.fakePasscodes.stream()
                    .filter(p -> p.activateByTimerTime != null && getActivatePasscodeTimerDuration() < p.activateByTimerTime && p.activateByTimerTime <= duration)
                    .sorted(Comparator.comparingLong(p -> p.activateByTimerTime))
                    .collect(Collectors.toList());
            if (!sortedPasscodes.isEmpty()) {
                for (FakePasscode passcode : sortedPasscodes) {
                    passcode.executeActions();
                }
                FakePasscode lastPasscode = sortedPasscodes.get(sortedPasscodes.size() - 1);
                SharedConfig.fakePasscodeActivated(SharedConfig.fakePasscodes.indexOf(lastPasscode));
                SharedConfig.saveConfig();
            }
        } catch (Exception ignore) {
        }
    }

    public static void scheduleFakePasscodeTimer(Context context) {
        try {
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            Intent intent = new Intent(context, AppStartReceiver.class);
            int flags = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0;
            PendingIntent pintent = PendingIntent.getBroadcast(context, 0, intent, flags);
            alarmManager.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + 60 * 1000, 5 * 60 * 1000, pintent);
            InnerFakePasscodeTimer.schedule();
        } catch (Exception ignore) {
        }
    }

    public static void hideFakePasscodeTraces() {
        Utils.updateMessagesPreview();
    }
}
