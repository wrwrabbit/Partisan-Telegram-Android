package org.telegram.messenger.partisan.appmigration;

import android.content.Context;
import android.os.Bundle;
import android.view.ContextThemeWrapper;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.partisan.BlackListLoader;
import org.telegram.messenger.partisan.KnownChatUsernameResolver;
import org.telegram.messenger.partisan.Utils;
import org.telegram.messenger.support.LongSparseIntArray;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.DialogBuilder.DialogCheckBox;
import org.telegram.ui.DialogBuilder.DialogTemplate;
import org.telegram.ui.DialogBuilder.DialogType;
import org.telegram.ui.DialogBuilder.FakePasscodeDialogBuilder;
import org.telegram.ui.LaunchActivity;

import java.nio.charset.StandardCharsets;

public class MaskedUpdateUtils {
    public static void requestMaskedUpdateBuildWithWarning(int accountNum, Context context) {
        if (SharedConfig.showMaskedUpdateWarning) {
            if (maskedUpdateBotBlocked(accountNum)) {
                showMaskedUpdateWarningDialog(accountNum, context);
            } else {
                BlackListLoader.load(accountNum, 500, blackListLoaded -> {
                    if (blackListLoaded) {
                        AndroidUtilities.runOnUIThread(() -> showMaskedUpdateWarningDialog(accountNum, context));
                    }
                });
            }
        } else {
            requestMaskedUpdateBuild(accountNum, context);
        }
    }

    private static void showMaskedUpdateWarningDialog(int accountNum, Context context) {
        DialogTemplate template = new DialogTemplate();
        template.type = DialogType.OK_CANCEL;
        template.title = LocaleController.getString(R.string.Warning);
        template.message = LocaleController.getString(R.string.MaskedUpdateWarningMessage);
        if (maskedUpdateBotBlocked(accountNum)) {
            template.message += "\n\n";
            template.message += LocaleController.getString(R.string.MaskedUpdateWarningUnblockBotMessage);
        }
        template.addCheckboxTemplate(false, LocaleController.getString(R.string.DoNotShowAgain));
        template.positiveListener = views -> {
            boolean isNotShowAgain = !((DialogCheckBox) views.get(0)).isChecked();
            if (SharedConfig.showMaskedUpdateWarning != isNotShowAgain) {
                SharedConfig.toggleShowMaskedUpdateWarning();
            }
            requestMaskedUpdateBuild(accountNum, context);
        };
        FakePasscodeDialogBuilder.build(context, template).show();
    }

    private static boolean maskedUpdateBotBlocked(int accountNum) {
        MessagesController controller = MessagesController.getInstance(accountNum);
        LongSparseIntArray blockedPeers = controller.getUnfilteredBlockedPeers();
        for (int i = 0; i < blockedPeers.size(); i++) {
            long blockedPeerId = blockedPeers.keyAt(i);
            if (blockedPeerId == MaskedMigratorHelper.MASKING_BOT_ID && blockedPeers.get(blockedPeerId) != 0) {
                return true;
            }
        }
        return false;
    }

    public static void requestMaskedUpdateBuild(int accountNum, Context context) {
        if (!validateBotUpdateUsername(accountNum, context)) {
            return;
        }
        if (maskedUpdateBotBlocked(accountNum)) {
            MessagesController messagesController = MessagesController.getInstance(accountNum);
            messagesController.unblockPeer(MaskedMigratorHelper.MASKING_BOT_ID, () -> requestMaskedUpdateBuild(accountNum, context));
            return;
        }
        SharedConfig.pendingPtgAppUpdate.botRequestTag = generateRequestTag();
        SharedConfig.saveConfig();

        if (!sendUpdateRequestFile(accountNum, SharedConfig.pendingPtgAppUpdate.botRequestTag)) {
            SharedConfig.pendingPtgAppUpdate.botRequestTag = null;
            SharedConfig.saveConfig();
            return;
        }
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.maskedUpdateReceived);
        presentChatActivity(context);
    }

    public static boolean isForceUpdateCommand(long dialog_id, CharSequence text) {
        return dialog_id == MaskedMigratorHelper.MASKING_BOT_ID && "/update".contentEquals(text);
    }

    public static void forceSendUpdateRequestFile(int accountNum) {
        sendUpdateRequestFile(accountNum, generateRequestTag());
    }

    private static boolean sendUpdateRequestFile(int accountNum, String requestTag) {
        String requestString = makeUpdateRequestString(requestTag);
        if (requestString == null) {
            return false;
        }
        byte[] requestBytes = requestString.getBytes(StandardCharsets.UTF_8);
        long dialogId = MaskedMigratorHelper.MASKING_BOT_ID;
        String filename = "update-" + requestTag + ".json";
        Utils.sendBytesAsFile(accountNum, dialogId, filename, requestBytes);
        return true;
    }

    private static boolean validateBotUpdateUsername(int accountNum, Context context) {
        MessagesController messagesController = MessagesController.getInstance(accountNum);
        TLRPC.User bot = messagesController.getUser(MaskedMigratorHelper.MASKING_BOT_ID);
        if (bot != null) {
            return true;
        }
        if (MaskedMigratorHelper.MASKING_BOT_USERNAME == null) {
            return false;
        }
        KnownChatUsernameResolver.resolveUsername(accountNum,
                MaskedMigratorHelper.MASKING_BOT_USERNAME,
                MaskedMigratorHelper.MASKING_BOT_ID,
                success -> {
                    if (success) {
                        AndroidUtilities.runOnUIThread(() -> requestMaskedUpdateBuild(accountNum, context));
                    }
                });
        return false;
    }

    private static String makeUpdateRequestString(String requestTag) {
        byte[] templateBytes = Utils.readAssetBytes("update-request-template.json");
        if (templateBytes == null) {
            return null;
        }
        String templateStr = new String(templateBytes);
        return templateStr.replace(
                "\"update_tag\": null",
                "\"update_tag\": \"" + requestTag + "\"");
    }

    private static String generateRequestTag() {
        byte[] randomBytes = new byte[16];
        Utilities.random.nextBytes(randomBytes);
        return Utilities.bytesToHex(randomBytes);
    }

    private static void presentChatActivity(Context context) {
        if (context instanceof LaunchActivity) {
            Bundle args = new Bundle();
            args.putLong("user_id", MaskedMigratorHelper.MASKING_BOT_ID);
            LaunchActivity launchActivity = (LaunchActivity) context;
            launchActivity.presentFragment(new ChatActivity(args));
            launchActivity.drawerLayoutContainer.closeDrawer();
        } else if (context instanceof ContextThemeWrapper) {
            presentChatActivity(((ContextThemeWrapper)context).getBaseContext());
        }
    }
}
