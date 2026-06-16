package org.telegram.messenger;

import android.content.Context;

import com.fasterxml.jackson.annotation.JsonIgnore;

import org.telegram.messenger.camera.HiddenCameraManager;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class BadPasscodeAttempt {
    public static final int AppUnlockType = 0;
    public static final int PasscodeSettingsType = 1;
    public int type;
    public boolean isFakePasscode;
    public LocalDateTime date;
    @Deprecated
    private String frontPhotoPath;
    @Deprecated
    private String backPhotoPath;
    public List<String> photoPaths = new ArrayList<>();

    public BadPasscodeAttempt() {}
    public BadPasscodeAttempt(int type, boolean isFakePasscode) {
        this.type = type;
        this.isFakePasscode = isFakePasscode;
        this.date = LocalDateTime.now();
    }

    @JsonIgnore
    public String getTypeString() {
        switch (type) {
            case AppUnlockType: return LocaleController.getString(R.string.AppUnlock);
            default:
            case PasscodeSettingsType: return LocaleController.getString(R.string.EnterPasswordSettings);
        }
    }

    public void takePhotos(Context context) {
        if (SharedConfig.takePhotoWithBadPasscodeFront) {
            takeSinglePhoto(context, true, () -> {
                if (SharedConfig.takePhotoWithBadPasscodeBack) {
                    takeSinglePhoto(context, false, null);
                }
            });
        } else if (SharedConfig.takePhotoWithBadPasscodeBack) {
            takeSinglePhoto(context, false, null);
        }
    }

    private void takeSinglePhoto(Context context, boolean front, Runnable onFinish) {
        (new HiddenCameraManager(context)).takePhoto(front, path -> {
            photoPaths.add(path);
            SharedConfig.saveBadPasscodeAttempts();
            if (onFinish != null) {
                onFinish.run();
            }
        });
    }

    public void clear() {
        for (String path : photoPaths) {
            new File(ApplicationLoader.getFilesDirFixed(), path).delete();
        }
        photoPaths.clear();
    }

    /** @noinspection deprecation*/
    public boolean migrate() {
        if (!photoPaths.isEmpty()) {
            return false;
        }
        if (frontPhotoPath != null) {
            photoPaths.add(frontPhotoPath);
            frontPhotoPath = null;
        }
        if (backPhotoPath != null) {
            photoPaths.add(backPhotoPath);
            backPhotoPath = null;
        }
        String prefix = ApplicationLoader.getFilesDirFixed() + File.separator;
        photoPaths = photoPaths.stream()
                .map(path -> path.replace(prefix, ""))
                .collect(Collectors.toList());
        return true;
    }
}
