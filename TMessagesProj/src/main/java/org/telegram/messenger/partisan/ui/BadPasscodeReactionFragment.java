package org.telegram.messenger.partisan.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.partisan.masked_ptg.MaskedPtgUtils;
import org.telegram.messenger.partisan.ui.items.AbstractViewItem;
import org.telegram.messenger.partisan.ui.items.ButtonItem;
import org.telegram.messenger.partisan.ui.items.DescriptionItem;
import org.telegram.messenger.partisan.ui.items.ToggleItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.BadPasscodeAttemptsActivity;
import org.telegram.ui.Components.AlertsCreator;

public class BadPasscodeReactionFragment extends PartisanBaseFragment {

    @Override
    protected String getTitle() {
        return getString(R.string.BadPasscodeReaction);
    }

    @Override
    protected AbstractViewItem[] createItems() {
        return new AbstractViewItem[]{
                new ToggleItem(this,
                        getString(R.string.BruteForceProtection),
                        () -> SharedConfig.bruteForceProtectionEnabled,
                        newValue -> {
                            SharedConfig.bruteForceProtectionEnabled = newValue;
                            SharedConfig.saveConfig();
                        }),
                new DescriptionItem(this, getString(R.string.BruteForceProtectionInfo)),
                new ButtonItem(this,
                        getString(R.string.BadPasscodeAttempts),
                        () -> String.valueOf(SharedConfig.getBadPasscodeAttemptList().size()),
                        v -> presentFragment(new BadPasscodeAttemptsActivity())),
                new ToggleItem(this,
                        getString(R.string.TakePhotoWithFrontCamera),
                        () -> SharedConfig.takePhotoWithBadPasscodeFront,
                        newValue -> toggleCameraPhoto(newValue, true))
                        .addCondition(() -> MaskedPtgUtils.hasPermission(getContext(), Manifest.permission.CAMERA)),
                new ToggleItem(this,
                        getString(R.string.TakePhotoWithBackCamera),
                        () -> SharedConfig.takePhotoWithBadPasscodeBack,
                        newValue -> toggleCameraPhoto(newValue, false))
                        .addCondition(() -> MaskedPtgUtils.hasPermission(getContext(), Manifest.permission.CAMERA)),
                new ToggleItem(this,
                        getString(R.string.MuteAudioWhenTakingPhoto),
                        () -> SharedConfig.takePhotoMuteAudio,
                        newValue -> {
                            SharedConfig.takePhotoMuteAudio = newValue;
                            SharedConfig.saveConfig();
                        })
                        .addCondition(() -> MaskedPtgUtils.hasPermission(getContext(), Manifest.permission.CAMERA)
                                && (SharedConfig.takePhotoWithBadPasscodeFront || SharedConfig.takePhotoWithBadPasscodeBack)),
                new DescriptionItem(this, getString(R.string.BadPasscodeAttemptsInfo)),
        };
    }

    private void toggleCameraPhoto(boolean newValue, boolean isFront) {
        if (!newValue) {
            if (isFront) {
                SharedConfig.takePhotoWithBadPasscodeFront = false;
            } else {
                SharedConfig.takePhotoWithBadPasscodeBack = false;
            }
            SharedConfig.saveConfig();
            afterPhotoToggle();
        } else {
            showPhotoWarning(() -> {
                Activity parentActivity = getParentActivity();
                if (ContextCompat.checkSelfPermission(parentActivity, Manifest.permission.CAMERA)
                        == PackageManager.PERMISSION_GRANTED) {
                    if (isFront) {
                        SharedConfig.takePhotoWithBadPasscodeFront = true;
                    } else {
                        SharedConfig.takePhotoWithBadPasscodeBack = true;
                    }
                    SharedConfig.saveConfig();
                    afterPhotoToggle();
                } else {
                    ActivityCompat.requestPermissions(parentActivity,
                            new String[]{Manifest.permission.CAMERA}, isFront ? 2000 : 2001);
                }
            });
        }
    }

    private void afterPhotoToggle() {
        if (listAdapter != null) {
            listAdapter.updateRows();
            listAdapter.notifyDataSetChanged();
        }
    }

    private void showPhotoWarning(Runnable callback) {
        if (SharedConfig.takePhotoWithBadPasscodeFront || SharedConfig.takePhotoWithBadPasscodeBack) {
            callback.run();
        } else {
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setMessage(LocaleController.getString(R.string.TakePhotoWarning));
            builder.setTitle(LocaleController.getString(R.string.Warning));
            builder.setPositiveButton(LocaleController.getString(R.string.OK), (d, v) -> callback.run());
            showDialog(builder.create());
        }
    }

    @Override
    public void onRequestPermissionsResultFragment(int requestCode, String[] permissions, int[] grantResults) {
        if ((requestCode == 2000 || requestCode == 2001) && grantResults.length > 0) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                AndroidUtilities.runOnUIThread(() -> {
                    if (requestCode == 2000) {
                        SharedConfig.takePhotoWithBadPasscodeFront = true;
                    } else {
                        SharedConfig.takePhotoWithBadPasscodeBack = true;
                    }
                    SharedConfig.saveConfig();
                    afterPhotoToggle();
                });
            } else {
                new AlertDialog.Builder(getParentActivity())
                        .setTopAnimation(R.raw.permission_request_camera, AlertsCreator.PERMISSIONS_REQUEST_TOP_ICON_SIZE, false, Theme.getColor(Theme.key_dialogTopBackground))
                        .setMessage(AndroidUtilities.replaceTags(LocaleController.getString(R.string.PermissionNoCameraWithHint)))
                        .setPositiveButton(LocaleController.getString(R.string.PermissionOpenSettings), (dialogInterface, i) -> {
                            try {
                                Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                intent.setData(Uri.parse("package:" + ApplicationLoader.applicationContext.getPackageName()));
                                getParentActivity().startActivity(intent);
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                        })
                        .setNegativeButton(LocaleController.getString(R.string.ContactsPermissionAlertNotNow), null)
                        .create()
                        .show();
            }
        }
    }
}
