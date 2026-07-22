package org.telegram.messenger.fakepasscode;

import com.fasterxml.jackson.annotation.JsonIgnore;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.StickersActivity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.telegram.messenger.MediaDataController.TYPE_IMAGE;

@FakePasscodeSerializer.ToggleSerialization
public class DeleteStickersAction extends AccountAction implements NotificationCenter.NotificationCenterDelegate {
    @JsonIgnore
    private static final int STEP_DELETE_REGULAR_STICKERS = 0;
    @JsonIgnore
    private static final int STEP_DELETE_ARCHIVED_STICKERS = 1;
    @JsonIgnore
    private int step = STEP_DELETE_REGULAR_STICKERS;
    @JsonIgnore
    private long lastUpdateTime = 0;
    @JsonIgnore
    private final AtomicInteger pendingStickerDeletions = new AtomicInteger();
    // NotificationCenter may postpone our bulletin past pendingStickerDeletions reaching 0 (e.g. during an
    // unrelated animation), but force-flushes it within EXPIRE_NOTIFICATIONS_TIME (~5017ms); wait a bit longer.
    @JsonIgnore
    private static final long BULLETIN_SUPPRESSION_TAIL_MS = 6000;
    @JsonIgnore
    private Runnable clearPreventBulletinRunnable;
    private boolean preventBulletin = false;

    @Override
    public void execute(FakePasscode fakePasscode) {
        step = STEP_DELETE_REGULAR_STICKERS;
        lastUpdateTime = 0;
        pendingStickerDeletions.set(0);
        loadStickers();
        //delete recent emoji
        Emoji.clearRecentEmoji();
        // delete recent gif
        for (TLRPC.Document document : MediaDataController.getInstance(accountNum).getRecentGifs()) {
            MediaDataController.getInstance(accountNum).removeRecentGif(document);
        }
    }

    private void loadStickers() {
        preventBulletin = true;
        cancelScheduledPreventBulletinClear();
        NotificationCenter.getInstance(accountNum).addObserver(this, NotificationCenter.stickersDidLoad);
        MediaDataController.getInstance(accountNum).loadStickers(TYPE_IMAGE, false, false, true, s -> deleteStickers());
    }

    private void deleteArchivedStickers() {
        TLRPC.TL_messages_getArchivedStickers req = new TLRPC.TL_messages_getArchivedStickers();
        req.offset_id = 0;
        req.limit = 100;
        req.masks = false;
        ConnectionsManager.getInstance(accountNum).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (error == null) {
                processArchivedStickersResponse((TLRPC.TL_messages_archivedStickers) response);
            }
        }));
    }


    private void processArchivedStickersResponse(TLRPC.TL_messages_archivedStickers res) {
        MediaDataController controller = MediaDataController.getInstance(accountNum);
        for (TLRPC.StickerSetCovered set : res.sets) {
            controller.toggleStickerSet(null, set, 2, null, false, false);
        }
        AndroidUtilities.runOnUIThread(this::loadStickers);
    }

    private synchronized void deleteStickers() {
        cancelScheduledPreventBulletinClear();
        MediaDataController controller = MediaDataController.getInstance(accountNum);
        List<TLRPC.TL_messages_stickerSet> stickerSets = new ArrayList<>(controller.getStickerSets(TYPE_IMAGE));
        for (TLRPC.TL_messages_stickerSet stickerSet : stickerSets) {
            pendingStickerDeletions.incrementAndGet();
            AndroidUtilities.runOnUIThread(() -> {
                controller.toggleStickerSet(null, stickerSet, 0, null, false, false);
                onStickerDeletionFinished();
            });
        }
        for (int recent_sticker_type = 0; recent_sticker_type < 8; recent_sticker_type++) {
            int typeFinal = recent_sticker_type;
            for (TLRPC.Document document : controller.getRecentStickers(recent_sticker_type)) {
                pendingStickerDeletions.incrementAndGet();
                AndroidUtilities.runOnUIThread(() -> {
                    controller.addRecentSticker(typeFinal, null, document, 0, true, false);
                    onStickerDeletionFinished();
                });
            }
        }
        controller.clearRecentStickers();
    }

    private void onStickerDeletionFinished() {
        if (pendingStickerDeletions.decrementAndGet() == 0) {
            scheduleBulletinSuppressionEnd();
        }
    }

    private void scheduleBulletinSuppressionEnd() {
        cancelScheduledPreventBulletinClear();
        clearPreventBulletinRunnable = () -> {
            clearPreventBulletinRunnable = null;
            if (pendingStickerDeletions.get() == 0) {
                preventBulletin = false;
            }
        };
        AndroidUtilities.runOnUIThread(clearPreventBulletinRunnable, BULLETIN_SUPPRESSION_TAIL_MS);
    }

    private void cancelScheduledPreventBulletinClear() {
        if (clearPreventBulletinRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(clearPreventBulletinRunnable);
            clearPreventBulletinRunnable = null;
        }
    }

    @Override
    public synchronized void didReceivedNotification(int id, int account, Object... args) {
        if (account != accountNum) {
            return;
        }
        if (lastUpdateTime != 0 && System.currentTimeMillis() - lastUpdateTime > 5000) {
            step = STEP_DELETE_REGULAR_STICKERS; // reset step
            NotificationCenter.getInstance(accountNum).removeObserver(this, NotificationCenter.stickersDidLoad);
            return;
        } else {
            lastUpdateTime = System.currentTimeMillis();
        }
        deleteStickers();
        if (step == STEP_DELETE_REGULAR_STICKERS) {
            step = STEP_DELETE_ARCHIVED_STICKERS;
            deleteArchivedStickers();
        }
    }

    public boolean isPreventBulletin() {
        return preventBulletin;
    }
}
