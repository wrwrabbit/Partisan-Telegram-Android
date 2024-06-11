package org.telegram.messenger.partisan.update;

import org.telegram.messenger.FileLoader;
import org.telegram.messenger.Utilities;

import java.io.File;

public class UpdateApkRemoveRunnable implements Runnable {
    @Override
    public void run() {
        try {
            File dir = FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE);
            File[] files = dir.listFiles(f -> f.getName().endsWith(".apk"));
            for (File f : files) {
                f.delete();
            }
        } catch (Exception ignore) {
            Utilities.cacheClearQueue.postRunnable(this, 1000);
        }
    }
}
