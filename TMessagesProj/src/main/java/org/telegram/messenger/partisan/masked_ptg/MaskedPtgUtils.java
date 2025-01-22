package org.telegram.messenger.partisan.masked_ptg;

import android.Manifest;
import android.app.Dialog;
import android.content.Context;
import android.content.pm.PackageManager;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.BasePermissionsActivity;
import org.telegram.ui.DialogBuilder.DialogButtonWithTimer;
import org.telegram.ui.DialogBuilder.DialogCheckBox;
import org.telegram.ui.DialogBuilder.DialogTemplate;
import org.telegram.ui.DialogBuilder.DialogType;
import org.telegram.ui.DialogBuilder.FakePasscodeDialogBuilder;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class MaskedPtgUtils {
    public static boolean hasPermission(Context context, String permission) {
        try {
            if (context == null) {
                return false;
            }
            String[] permissions = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), PackageManager.GET_PERMISSIONS)
                    .requestedPermissions;
            return Arrays.asList(permissions).stream()
                    .anyMatch(p -> p != null && p.equals(permission));
        } catch (PackageManager.NameNotFoundException ignore) {
            return false;
        }
    }

    public static boolean hasAllPermissions(Context context, String[] requestedPermissions) {
        try {
            if (context == null) {
                return false;
            }
            String[] manifestPermissions = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), PackageManager.GET_PERMISSIONS)
                    .requestedPermissions;
            Set<String> manifestPermissionSet = new HashSet<>(Arrays.asList(manifestPermissions));
            return Arrays.asList(requestedPermissions).stream()
                    .allMatch(manifestPermissionSet::contains);
        } catch (PackageManager.NameNotFoundException ignore) {
            return false;
        }
    }

    public static boolean needShowPermissionsDisabledDialog(int requestCode, String[] permissions) {
        if (!SharedConfig.showPermissionDisabledDialog) {
            return false;
        }
        if (requestCode == 17 || requestCode == BasePermissionsActivity.REQUEST_CODE_CALLS) {
            return false;
        }
        if (requestCode == 1 && Arrays.asList(permissions).contains(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            return false;
        }
        return true;
    }

    public static Dialog createPermissionDisabledDialog(Context ctx) {
        DialogTemplate template = new DialogTemplate();
        template.type = DialogType.OK;
        template.title = LocaleController.getString(R.string.PermissionDisabledTitle);
        template.message = LocaleController.getString(R.string.PermissionDisabledMessage);
        template.addCheckboxTemplate(false, LocaleController.getString("DoNotShowAgain", R.string.DoNotShowAgain));
        template.positiveListener = views -> {
            boolean isNotShowAgain = !((DialogCheckBox) views.get(0)).isChecked();
            if (SharedConfig.showPermissionDisabledDialog != isNotShowAgain) {
                SharedConfig.showPermissionDisabledDialog = isNotShowAgain;
                SharedConfig.saveConfig();
            }
        };
        return FakePasscodeDialogBuilder.build(ctx, template);
    }
}
