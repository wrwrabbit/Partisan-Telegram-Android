package org.telegram.messenger.partisan.update;

import android.text.TextUtils;

import com.google.android.exoplayer2.util.Consumer;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.partisan.AbstractChannelChecker;
import org.telegram.messenger.partisan.PartisanLog;
import org.telegram.messenger.partisan.settings.TesterSettings;
import org.telegram.tgnet.TLRPC;

import java.util.List;
import java.util.Objects;

public class UpdateChecker extends AbstractChannelChecker {
    public interface UpdateCheckedDelegate {
        void onUpdateResult(UpdateData update);
    }

    private final long CYBER_PARTISAN_SECURITY_TG_CHANNEL_ID = BuildVars.isAlphaApp() ? -1716369838 : -1808776994;  // For checking for updates
    private final String CYBER_PARTISAN_SECURITY_TG_CHANNEL_USERNAME = BuildVars.isAlphaApp() ? "ptg_update_test" : "ptgprod";
    private static final int CURRENT_FORMAT_VERSION = 1;
    private UpdateCheckedDelegate delegate;

    private UpdateChecker(int currentAccount) {
        super(currentAccount, null);
    }

    public static void checkUpdate(int currentAccount, UpdateCheckedDelegate delegate) {
        PartisanLog.d("UpdateChecker: checkUpdate");
        UpdateChecker checker = new UpdateChecker(currentAccount);
        checker.delegate = (data) -> {
            if (data != null) {
                PartisanLog.d("UpdateChecker: update found: " + data.version.toString());
            } else {
                PartisanLog.d("UpdateChecker: update not found");
            }
            checker.removeObservers();
            delegate.onUpdateResult(data);
        };
        checker.checkUpdate();
    }

    @Override
    protected void processChannelMessages(List<MessageObject> messages) {
        PartisanLog.d("UpdateChecker: processChannelMessages " + messages.size());
        UpdateData update = getMaxUpdateDataFromMessages(messages);
        if (update != null && update.stickerPackName != null && update.stickerEmoji != null) {
            PartisanLog.d("UpdateChecker: load sticker");
            loadStickerByEmoji(update.stickerPackName, update.stickerEmoji, sticker -> {
                PartisanLog.d("UpdateChecker: sticker loaded");
                update.sticker = sticker;
                AndroidUtilities.runOnUIThread(() -> delegate.onUpdateResult(update));
            });
        } else {
            delegate.onUpdateResult(update);
        }
    }

    private UpdateData getMaxUpdateDataFromMessages(List<MessageObject> messages) {
        UpdateData update = null;
        UpdateMessageParser parser = new UpdateMessageParser(currentAccount);
        for (MessageObject message : sortMessageById(messages)) {
            CharSequence messageText = message.messageText != null ? message.messageText : "<empty>";
            PartisanLog.d("UpdateChecker: process message, messageText.length() - " + messageText.length());
            UpdateData currentUpdate = parser.processMessage(message);
            if (currentUpdate != null) {
                PartisanLog.d("UpdateChecker: current update version is " + currentUpdate.version + ", app version is " + AppVersion.getCurrentVersion());
            } else {
                PartisanLog.d("UpdateChecker: current update is null");
            }
            if (isCurrentUpdateBetterThenPrevious(currentUpdate, update)) {
                PartisanLog.d("UpdateChecker: current update is greater than previous");
                update = currentUpdate;
            }
        }
        return update;
    }

    private boolean isCurrentUpdateBetterThenPrevious(UpdateData currentUpdate, UpdateData previousUpdate) {
        if (currentUpdate == null || currentUpdate.formatVersion > CURRENT_FORMAT_VERSION) {
            return false;
        }
        if (previousUpdate == null) {
            return AppVersion.greater(currentUpdate.version, AppVersion.getCurrentVersion());
        } else {
            return AppVersion.greater(currentUpdate.version, previousUpdate.version);
        }
    }

    private void loadStickerByEmoji(String stickerPackName, String stickerEmoji, Consumer<TLRPC.Document> onFinish) {
        TLRPC.TL_messages_stickerSet stickerSet = getMediaDataController().getStickerSetByName(stickerPackName);
        if (stickerSet != null) {
            onFinish.accept(getStickerByEmoji(stickerSet, stickerEmoji));
        } else {
            TLRPC.TL_messages_getStickerSet req = createGetStickerRequest(stickerPackName);
            getConnectionsManager().sendRequest(req, (response, error) -> {
                if (response != null) {
                    onFinish.accept(getStickerByEmoji((TLRPC.TL_messages_stickerSet) response, stickerEmoji));
                } else {
                    onFinish.accept(null);
                }
            });
        }
    }

    private static TLRPC.TL_messages_getStickerSet createGetStickerRequest(String stickerPackName) {
        TLRPC.TL_messages_getStickerSet req = new TLRPC.TL_messages_getStickerSet();
        req.stickerset = new TLRPC.TL_inputStickerSetShortName();
        req.stickerset.short_name = stickerPackName;
        return req;
    }

    private TLRPC.Document getStickerByEmoji(TLRPC.TL_messages_stickerSet stickerSet, String emoji) {
        if (emoji == null || TextUtils.isEmpty(emoji)) {
            return null;
        }
        for (TLRPC.Document document : stickerSet.documents) {
            String stickerEmoji = getStickerChar(document);
            if (stickerEmoji != null && stickerEmoji.equals(emoji)) {
                return document;
            }
        }
        return null;
    }

    private String getStickerChar(TLRPC.Document document) {
        for (TLRPC.DocumentAttribute attribute : document.attributes) {
            if (attribute instanceof TLRPC.TL_documentAttributeSticker) {
                return attribute.alt;
            }
        }
        return null;
    }

    @Override
    protected void messagesLoadingError() {
        PartisanLog.d("Update messagesLoadingError");
        delegate.onUpdateResult(null);
    }

    @Override
    protected long getChannelId() {
        if (TesterSettings.updateChannelIdOverride.get() != 0) {
            return TesterSettings.updateChannelIdOverride.get();
        } else {
            return CYBER_PARTISAN_SECURITY_TG_CHANNEL_ID;
        }
    }

    @Override
    protected String getChannelUsername() {
        if (!Objects.equals(TesterSettings.updateChannelUsernameOverride, "")) {
            return TesterSettings.updateChannelUsernameOverride.get();
        } else {
            return CYBER_PARTISAN_SECURITY_TG_CHANNEL_USERNAME;
        }
    }
}
