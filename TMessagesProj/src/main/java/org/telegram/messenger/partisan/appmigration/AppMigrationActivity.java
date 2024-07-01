package org.telegram.messenger.partisan.appmigration;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RadialProgressView;
import org.telegram.ui.Components.voip.CellFlickerDrawable;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.ThreadLocalRandom;

public class AppMigrationActivity extends BaseFragment implements AppMigrator.MakeZipDelegate {
    RelativeLayout relativeLayout;
    private TextView titleTextView;
    private TextView descriptionText;
    private RadialProgressView progressBar;
    private TextView buttonTextView;
    private boolean destroyed;
    private long spaceSizeNeeded;

    private final static int cancel = 100;

    public AppMigrationActivity() {
        super();
    }

    @Override
    public View createView(Context context) {
        createActionBar();
        createFragmentView(context);
        createRelativeLayout(context);
        createDescriptionText(context);
        createTitleTextView(context);
        createProgressBar(context);
        createButton(context);
        if (AppMigrator.getStep() == Step.NOT_STARTED) {
            AppMigrator.setStep(Step.MAKE_ZIP);
        }
        if (AppMigrator.getStep() == Step.MAKE_ZIP) {
            makeZip();
        }

        new Thread(this::checkThread).start();
        updateUI();

        return fragmentView;
    }

    private void createActionBar() {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(false);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == cancel) {
                    setStep(Step.NOT_STARTED);
                    if (!AppMigrator.isNewerPtgInstalled(getContext(), false)) {
                        AppMigrator.enableConnection();
                    }
                    finishFragment();
                }
            }
        });

        ActionBarMenu menu = actionBar.createMenu();
        menu.addItem(cancel, new BackDrawable(true));
    }

    private void createFragmentView(Context context) {
        fragmentView = new FrameLayout(context);
        FrameLayout frameLayout = (FrameLayout) fragmentView;

        actionBar.setTitle(LocaleController.getString(R.string.UpdaterActivityTitle));
        frameLayout.setTag(Theme.key_windowBackgroundGray);
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
    }

    private void createRelativeLayout(Context context) {
        relativeLayout = new RelativeLayout(context);
        ((FrameLayout) fragmentView).addView(relativeLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
    }

    private void createProgressBar(Context context) {
        progressBar = new RadialProgressView(context);
        progressBar.setSize(AndroidUtilities.dp(32));
        RelativeLayout.LayoutParams relativeParams = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        relativeParams.addRule(RelativeLayout.CENTER_HORIZONTAL);
        relativeParams.addRule(RelativeLayout.BELOW, descriptionText.getId());
        progressBar.setVisibility(View.GONE);
        relativeLayout.addView(progressBar, relativeParams);
    }

    private void createTitleTextView(Context context) {
        titleTextView = new TextView(context);
        titleTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        titleTextView.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        titleTextView.setPadding(AndroidUtilities.dp(32), 0, AndroidUtilities.dp(32), 0);
        titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 24);
        RelativeLayout.LayoutParams relativeParams = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        relativeParams.addRule(RelativeLayout.CENTER_HORIZONTAL);
        relativeParams.addRule(RelativeLayout.ABOVE, descriptionText.getId());
        relativeLayout.addView(titleTextView, relativeParams);
    }

    private void createDescriptionText(Context context) {
        descriptionText = new TextView(context);
        descriptionText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText6));
        descriptionText.setGravity(Gravity.CENTER_HORIZONTAL);
        descriptionText.setLineSpacing(AndroidUtilities.dp(2), 1);
        descriptionText.setPadding(AndroidUtilities.dp(32), 0, AndroidUtilities.dp(32), 0);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            descriptionText.setId(View.generateViewId());
        } else {
            descriptionText.setId(ThreadLocalRandom.current().nextInt(Integer.MAX_VALUE / 2, Integer.MAX_VALUE));
        }
        RelativeLayout.LayoutParams relativeParams = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        relativeParams.addRule(RelativeLayout.CENTER_IN_PARENT);
        relativeLayout.addView(descriptionText, relativeParams);
    }

    private void createButton(Context context) {
        buttonTextView = new TextView(context) {
            CellFlickerDrawable cellFlickerDrawable;

            @Override
            protected void onDraw(Canvas canvas) {
                if (isEnabled()) {
                    super.onDraw(canvas);
                    if (cellFlickerDrawable == null) {
                        cellFlickerDrawable = new CellFlickerDrawable();
                        cellFlickerDrawable.drawFrame = false;
                        cellFlickerDrawable.repeatProgress = 2f;
                    }
                    cellFlickerDrawable.setParentWidth(getMeasuredWidth());
                    AndroidUtilities.rectTmp.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
                    cellFlickerDrawable.draw(canvas, AndroidUtilities.rectTmp, AndroidUtilities.dp(4), null);
                    invalidate();
                } else {
                    super.onDraw(canvas);
                }
            }

            @Override
            public void setEnabled(boolean enabled) {
                super.setEnabled(enabled);
                Drawable drawable = Theme.createSimpleSelectorRoundRectDrawable(
                        AndroidUtilities.dp(4),
                        enabled ? Theme.getColor(Theme.key_featuredStickers_addButton)
                                : Theme.getColor(Theme.key_picker_disabledButton),
                        Theme.getColor(Theme.key_featuredStickers_addButtonPressed)
                );
                buttonTextView.setBackground(drawable);
            }
        };

        buttonTextView.setPadding(0, AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12));
        buttonTextView.setGravity(Gravity.CENTER);
        buttonTextView.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        buttonTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        buttonTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        buttonTextView.setEnabled(true);
        RelativeLayout.LayoutParams relativeParams = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        relativeParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        relativeParams.addRule(RelativeLayout.CENTER_HORIZONTAL);
        relativeParams.setMargins(AndroidUtilities.dp(32), 0, AndroidUtilities.dp(32), AndroidUtilities.dp(32));
        relativeLayout.addView(buttonTextView, relativeParams);
        buttonTextView.setOnClickListener(v -> buttonClicked());
    }

    @Override
    public void onFragmentDestroy() {
        destroyed = true;
        super.onFragmentDestroy();
    }

    private void setStep(Step step) {
        AppMigrator.setStep(step);
        AndroidUtilities.runOnUIThread(this::updateUI);
    }

    private void updateUI() {
        titleTextView.setText(getStepName(AppMigrator.getStep()));
        descriptionText.setText(getStepDescription());
        buttonTextView.setText(getButtonName());
        buttonTextView.setEnabled(getStepButtonEnabled(AppMigrator.getStep()));
        progressBar.setVisibility(AppMigrator.getStep() == Step.MAKE_ZIP ? View.VISIBLE : View.GONE);
    }

    private static String getStepName(Step step) {
        Step simplifiedStep = step.simplify();
        if (simplifiedStep == Step.MAKE_ZIP) {
            return LocaleController.getString(R.string.TransferringFilesStep);
        } else if (simplifiedStep == Step.UNINSTALL_SELF) {
            return LocaleController.getString(R.string.UninstallSelfStep);
        } else {
            return null;
        }
    }

    private String getStepDescription() {
        switch (AppMigrator.getStep()) {
            case MAKE_ZIP:
            default:
                return LocaleController.formatString(R.string.ZipPreparingDescription, LocaleController.getString(R.string.TransferFilesToAnotherTelegramButton));
            case MAKE_ZIP_FAILED:
                return LocaleController.formatString(R.string.ZipPreparationFailedDescription, LocaleController.getString(R.string.Retry), LocaleController.getString(R.string.MigrationContactPtgSupport));
            case MAKE_ZIP_LOCKED:
                return String.format(LocaleController.getString(R.string.NoSpaceForStep), (double)spaceSizeNeeded / 1024.0 / 1024.0);
            case MAKE_ZIP_COMPLETED:
                return LocaleController.formatString(R.string.ZipPreparedDescription, LocaleController.getString(R.string.TransferFilesToAnotherTelegramButton));
            case OPEN_NEW_TELEGRAM_FAILED:
                return LocaleController.formatString(R.string.OpenNewTelegramFailedDescription, LocaleController.getString(R.string.Retry), LocaleController.getString(R.string.MigrationContactPtgSupport));
            case UNINSTALL_SELF:
                return LocaleController.getString(R.string.UninstallSelfDescription);
        }
    }

    private String getButtonName() {
        switch (AppMigrator.getStep()) {
            default:
            case MAKE_ZIP:
            case MAKE_ZIP_COMPLETED:
            case MAKE_ZIP_LOCKED:
                return LocaleController.getString(R.string.TransferFilesToAnotherTelegramButton);
            case UNINSTALL_SELF:
                return LocaleController.getString(R.string.UninstallSelf);
            case MAKE_ZIP_FAILED:
            case OPEN_NEW_TELEGRAM_FAILED:
                return LocaleController.getString(R.string.Retry);
        }
    }

    private static boolean getStepButtonEnabled(Step step) {
        switch (step) {
            case MAKE_ZIP_FAILED:
            case MAKE_ZIP_COMPLETED:
            case OPEN_NEW_TELEGRAM_FAILED:
            case UNINSTALL_SELF:
                return true;
            default:
                return false;
        }
    }

    private synchronized void buttonClicked() {
        switch (AppMigrator.getStep()) {
            case MAKE_ZIP:
            case MAKE_ZIP_FAILED:
            case OPEN_NEW_TELEGRAM_FAILED:
                makeZip();
                break;
            case MAKE_ZIP_COMPLETED:
                if (AppMigrator.allowStartNewTelegram()) {
                    boolean success = AppMigrator.startNewTelegram(getParentActivity());
                    if (!success) {
                        setStep(Step.OPEN_NEW_TELEGRAM_FAILED);
                    }
                } else {
                    makeZip();
                }
                break;
            case UNINSTALL_SELF:
                AppMigrator.uninstallSelf(getParentActivity());
                break;
        }
    }

    @Override
    public void makeZipCompleted() {
        AndroidUtilities.runOnUIThread(() -> setStep(Step.MAKE_ZIP_COMPLETED));
    }

    @Override
    public void makeZipFailed() {
        AndroidUtilities.runOnUIThread(() -> {
            setStep(Step.MAKE_ZIP_FAILED);
        });
    }

    private void makeZip() {
        long zipSize = calculateZipSize();
        if (zipSize > getFreeMemorySize()) {
            spaceSizeNeeded = zipSize;
            setStep(Step.MAKE_ZIP_LOCKED);
            return;
        }
        setStep(Step.MAKE_ZIP);
        new Thread(() -> {
            AppMigrator.enableConnection();
            AppMigrator.makeZip(getParentActivity(), this);
        }).start();
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        super.onActivityResultFragment(requestCode, resultCode, data);
        if (requestCode == 20202020) {
            if (resultCode == Activity.RESULT_OK && data != null && data.hasExtra("success")) {
                if (data.getBooleanExtra("success", false)) {
                    AppMigrator.disableConnection();
                    migrationFinished(data.getStringExtra("packageName"));
                } else {
                    AppMigrator.enableConnection();
                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                    builder.setTitle(LocaleController.getString(R.string.MigrationTitle));
                    String error = data.getStringExtra("error");
                    builder.setMessage(getErrorMessage(error));
                    builder.setPositiveButton(LocaleController.getString(R.string.OK), null);
                    showDialog(builder.create());
                }
            } else {
                AppMigrator.enableConnection();
            }
        }
    }

    private static String getErrorMessage(String error) {
        if ("alreadyHasAccounts".equals(error)) {
            return LocaleController.getString(R.string.MigrationErrorAlreadyHasAccountsDescription);
        } else if ("srcVersionGreater".equals(error)) {
            return LocaleController.formatString(R.string.MigrationErrorSrcVersionGreaterDescription, LocaleController.getString(R.string.MigrationContactPtgSupport));
        } else if ("srcVersionOld".equals(error)) {
            return LocaleController.getString(R.string.MigrationErrorSrcVersionOldDescription);
        } else {
            return LocaleController.formatString(R.string.MigrationErrorUnknownDescription, LocaleController.getString(R.string.MigrationContactPtgSupport));
        }
    }

    private void checkThread() {
        while (!destroyed) {
            try {
                synchronized (this) {
                    long freeSize = getFreeMemorySize();
                    if (AppMigrator.getStep() == Step.MAKE_ZIP_LOCKED) {
                        if (calculateZipSize() <= freeSize) {
                            makeZip();
                        }
                    }
                }
                Thread.sleep(100);
            } catch (Exception e) {
                Log.e("AppMigrator", "copyUpdaterFileFromAssets error ", e);
            }
        }
    }

    private long getFreeMemorySize() {
        File internalStorageFile = getParentActivity().getFilesDir();
        return internalStorageFile.getFreeSpace();
    }

    private long calculateZipSize() {
        File filesDir = getParentActivity().getFilesDir();
        long size = calculateDirSize(filesDir)
                + calculateDirSize(new File(filesDir.getParentFile(), "shared_prefs"));
        if (Build.VERSION.SDK_INT >= 24) {
            size *= 2;
        }
        return size;
    }

    private void migrationFinished(String packageName) {
        AppMigrator.setMigrationFinished(packageName);
        AppMigrator.deleteZipFile();
        setStep(Step.UNINSTALL_SELF);
    }

    private static long calculateDirSize(File dir) {
        if (!dir.exists()) {
            return 0;
        }
        long result = 0;
        File[] fileList = dir.listFiles();
        if (fileList != null) {
            for (File file : fileList) {
                if (file.isDirectory()) {
                    result += calculateDirSize(file);
                } else {
                    result += file.length();
                }
            }
        }
        return result;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!AppMigrator.checkMigrationNeedToResume(getContext())) {
            finishFragment();
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();

        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND | ThemeDescription.FLAG_CHECKTAG, null, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND | ThemeDescription.FLAG_CHECKTAG, null, null, null, null, Theme.key_windowBackgroundGray));

        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUBACKGROUND, null, null, null, null, Theme.key_actionBarDefaultSubmenuBackground));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUITEM, null, null, null, null, Theme.key_actionBarDefaultSubmenuItem));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUITEM | ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_actionBarDefaultSubmenuItemIcon));

        return themeDescriptions;
    }
}