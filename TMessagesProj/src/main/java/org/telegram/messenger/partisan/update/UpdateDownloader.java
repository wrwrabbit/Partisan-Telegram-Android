package org.telegram.messenger.partisan.update;

import androidx.collection.LongSparseArray;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.partisan.PartisanLog;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.LaunchActivity;

public class UpdateDownloader implements NotificationCenter.NotificationCenterDelegate {
    private final int accountNum;
    private int loadAttempt = 0;
    public static volatile boolean isUpdateChecking = false;

    public UpdateDownloader(int accountNum) {
        this.accountNum = accountNum;
    }

    public void startUpdateDownloading() {
        log("startUpdateDownloading");
        if (LaunchActivity.getUpdateAccountNum() != accountNum || SharedConfig.pendingPtgAppUpdate.message == null) {
            recheckUpdateAndStartDownloadAgain();
            return;
        } else {
            log("The pending update is correct");
        }
        MessageObject messageObject = new MessageObject(
                LaunchActivity.getUpdateAccountNum(),
                SharedConfig.pendingPtgAppUpdate.message,
                (LongSparseArray<TLRPC.User>) null,
                null,
                false,
                true
        );
        log("Update file loading started");
        getFileLoader().loadFile(SharedConfig.pendingPtgAppUpdate.document, messageObject, FileLoader.PRIORITY_NORMAL, 1);
        getNotificationCenter().addObserver(this, NotificationCenter.fileLoaded);
        getNotificationCenter().addObserver(this, NotificationCenter.fileLoadFailed);
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.updateDownloadingStarted);
    }

    private void recheckUpdateAndStartDownloadAgain() {
        log("The pending update is from another account or the update message is null");
        isUpdateChecking = true;
        UpdateChecker.checkUpdate(accountNum, data -> {
            log("The update rechecked");
            isUpdateChecking = false;
            if (data != null) {
                log("Correct update found");
                SharedConfig.pendingPtgAppUpdate = data;
                SharedConfig.saveConfig();
                AndroidUtilities.runOnUIThread(this::startUpdateDownloading);
            }
        });
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.fileLoaded) {
            if (isUpdatePath((String) args[0])) {
                log("update loaded");
                getNotificationCenter().removeObserver(this, NotificationCenter.fileLoaded);
                getNotificationCenter().removeObserver(this, NotificationCenter.fileLoadFailed);
            }
        } else if (id == NotificationCenter.fileLoadFailed) {
            if (isUpdatePath((String) args[0])) {
                log("update load failed");
                if (loadAttempt == 0) {
                    loadAttempt++;
                    recheckUpdateAndStartDownloadAgain();
                }
            }
        }
    }

    private boolean isUpdatePath(String path) {
        if (!SharedConfig.isAppUpdateAvailable()) {
            return false;
        }
        String name = FileLoader.getAttachFileName(SharedConfig.pendingPtgAppUpdate.document);
        return name.equals(path);
    }

    private void log(String message) {
        PartisanLog.d("UpdateDownloader: " + message);
    }

    private FileLoader getFileLoader() {
        return FileLoader.getInstance(accountNum);
    }

    private NotificationCenter getNotificationCenter() {
        return NotificationCenter.getInstance(accountNum);
    }
}
