package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.partisan.appmigration.MaskedUpdateUtils;
import org.telegram.messenger.partisan.update.UpdateChecker;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.IUpdateLayout;
import org.telegram.ui.LaunchActivity;

import java.io.File;
import java.util.ArrayList;

public class UpdateLayout extends IUpdateLayout implements NotificationCenter.NotificationCenterDelegate {

    private FrameLayout updateLayout;
    private RadialProgress2 updateLayoutIcon;
    private SimpleTextView[] updateTextViews;
    private TextView updateSizeTextView;
    private AnimatorSet updateTextAnimator;

    private Activity activity;
    private ViewGroup sideMenu;
    private ViewGroup sideMenuContainer;

    private Runnable onUpdateLayoutClicked;

    public UpdateLayout(Activity activity, ViewGroup sideMenu, ViewGroup sideMenuContainer) {
        super(activity, sideMenu, sideMenuContainer);
        this.activity = activity;
        this.sideMenu = sideMenu;
        this.sideMenuContainer = sideMenuContainer;
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.maskedUpdateReceived);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.updateDownloadingStarted);
    }

    public void updateFileProgress(Object[] args) {
        if (updateTextViews == null || args == null) return;
        if (updateTextViews[0] != null && SharedConfig.isAppUpdateAvailable()) {
            String location = (String) args[0];
            String fileName = FileLoader.getAttachFileName(SharedConfig.pendingPtgAppUpdate.document);
            if (fileName != null && fileName.equals(location)) {
                Long loadedSize = (Long) args[1];
                Long totalSize = (Long) args[2];
                float loadProgress = loadedSize / (float) totalSize;
                updateLayoutIcon.setProgress(loadProgress, true);
                updateTextViews[0].setText(LocaleController.formatString("AppUpdateDownloading", R.string.AppUpdateDownloading, (int) (loadProgress * 100)));
            }
        }
    }

    public void createUpdateUI(int currentAccount) {
        if (sideMenuContainer == null || updateLayout != null) {
            return;
        }
        updateLayout = new FrameLayout(activity) {

            private Paint paint = new Paint();
            private Matrix matrix = new Matrix();
            private LinearGradient updateGradient;
            private int lastGradientWidth;

            @Override
            public void draw(Canvas canvas) {
                if (updateGradient != null) {
                    paint.setColor(0xffffffff);
                    paint.setShader(updateGradient);
                    updateGradient.setLocalMatrix(matrix);
                    canvas.drawRect(0, 0, getMeasuredWidth(), getMeasuredHeight(), paint);
                    updateLayoutIcon.setBackgroundGradientDrawable(updateGradient);
                    updateLayoutIcon.draw(canvas);
                }
                super.draw(canvas);
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                int width = MeasureSpec.getSize(widthMeasureSpec);
                if (lastGradientWidth != width) {
                    updateGradient = new LinearGradient(0, 0, width, 0, new int[]{0xff69BF72, 0xff53B3AD}, new float[]{0.0f, 1.0f}, Shader.TileMode.CLAMP);
                    lastGradientWidth = width;
                }
            }
        };
        updateLayout.setWillNotDraw(false);
        updateLayout.setVisibility(View.INVISIBLE);
        updateLayout.setTranslationY(AndroidUtilities.dp(44));
        if (Build.VERSION.SDK_INT >= 21) {
            updateLayout.setBackground(Theme.getSelectorDrawable(0x40ffffff, false));
        }
        sideMenuContainer.addView(updateLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 44, Gravity.LEFT | Gravity.BOTTOM));
        updateLayout.setOnClickListener(v -> {
            if (!SharedConfig.isAppUpdateAvailable()) {
                return;
            }
            if (onUpdateLayoutClicked != null) {
                onUpdateLayoutClicked.run();
            }
            if (updateLayoutIcon.getIcon() == MediaActionDrawable.ICON_DOWNLOAD) {
                String botRequestTag = SharedConfig.pendingPtgAppUpdate.botRequestTag;
                if (botRequestTag == null) {
                    MaskedUpdateUtils.requestMaskedUpdateBuildWithWarning(currentAccount, activity);
                } else if (SharedConfig.pendingPtgAppUpdate.isMaskedUpdateDocument()) {
                    UpdateChecker.startUpdateDownloading(currentAccount);
                }
                updateAppUpdateViews(currentAccount,  true);
            } else if (updateLayoutIcon.getIcon() == MediaActionDrawable.ICON_CANCEL) {
                FileLoader.getInstance(currentAccount).cancelLoadFile(SharedConfig.pendingPtgAppUpdate.document);
                updateAppUpdateViews(currentAccount, true);
            } else if (SharedConfig.pendingPtgAppUpdate.isMaskedUpdateDocument()) {
                if (!AndroidUtilities.openForView(SharedConfig.pendingPtgAppUpdate.document, true, activity)) {
                    UpdateChecker.startUpdateDownloading(currentAccount);
                }
            }
        });
        updateLayoutIcon = new RadialProgress2(updateLayout);
        updateLayoutIcon.setColors(0xffffffff, 0xffffffff, 0xffffffff, 0xffffffff);
        updateLayoutIcon.setProgressRect(AndroidUtilities.dp(22), AndroidUtilities.dp(11), AndroidUtilities.dp(22 + 22), AndroidUtilities.dp(11 + 22));
        updateLayoutIcon.setCircleRadius(AndroidUtilities.dp(11));
        updateLayoutIcon.setAsMini();

        updateTextViews = new SimpleTextView[2];
        for (int i = 0; i < 2; ++i) {
            updateTextViews[i] = new SimpleTextView(activity);
            updateTextViews[i].setTextSize(15);
            updateTextViews[i].setTypeface(AndroidUtilities.bold());
            updateTextViews[i].setTextColor(0xffffffff);
            updateTextViews[i].setGravity(Gravity.LEFT);
            updateLayout.addView(updateTextViews[i], LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 74, 0, 0, 0));
        }
        updateTextViews[0].setText(LocaleController.getString(R.string.RequestUpdate));
        updateTextViews[1].setAlpha(0f);
        updateTextViews[1].setVisibility(View.GONE);

        updateSizeTextView = new TextView(activity);
        updateSizeTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        updateSizeTextView.setTypeface(AndroidUtilities.bold());
        updateSizeTextView.setGravity(Gravity.RIGHT);
        updateSizeTextView.setTextColor(0xffffffff);
        updateLayout.addView(updateSizeTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.RIGHT, 0, 0, 17, 0));
    }

    public void updateAppUpdateViews(int currentAccount, boolean animated) {
        if (sideMenuContainer == null) {
            return;
        }
        if (SharedConfig.isAppUpdateAvailable()) {
            createUpdateUI(currentAccount);
            updateSizeTextView.setText(AndroidUtilities.formatFileSize(SharedConfig.pendingPtgAppUpdate.document.size));
            String fileName = FileLoader.getAttachFileName(SharedConfig.pendingPtgAppUpdate.document);
            File path = FileLoader.getInstance(LaunchActivity.getUpdateAccountNum()).getPathToAttach(SharedConfig.pendingPtgAppUpdate.document, true);
            String botRequestTag = SharedConfig.pendingPtgAppUpdate.botRequestTag;
            boolean showSize;
            if (path.exists() && botRequestTag != null) {
                updateLayoutIcon.setIcon(MediaActionDrawable.ICON_UPDATE, true, animated);
                setUpdateText(LocaleController.getString(R.string.AppUpdateNow), animated);
                showSize = false;
            } else {
                if (botRequestTag == null) {
                    updateLayoutIcon.setIcon(MediaActionDrawable.ICON_DOWNLOAD, true, animated);
                    setUpdateText(LocaleController.getString(R.string.RequestUpdate), animated);
                    showSize = false;
                } else if (!SharedConfig.pendingPtgAppUpdate.isMaskedUpdateDocument()) {
                    updateLayoutIcon.setIcon(MediaActionDrawable.ICON_WAIT_BUILD_MASKED_UPDATE, true, animated);
                    setUpdateText(LocaleController.getString(R.string.UpdateBuildWaiting), animated);
                    showSize = false;
                } else if (FileLoader.getInstance(LaunchActivity.getUpdateAccountNum()).isLoadingFile(fileName) || UpdateChecker.isUpdateChecking) {
                    updateLayoutIcon.setIcon(MediaActionDrawable.ICON_CANCEL, true, animated);
                    updateLayoutIcon.setProgress(0, false);
                    Float p = ImageLoader.getInstance().getFileProgress(fileName);
                    setUpdateText(LocaleController.formatString("AppUpdateDownloading", R.string.AppUpdateDownloading, (int) ((p != null ? p : 0.0f) * 100)), animated);
                    showSize = false;
                } else {
                    updateLayoutIcon.setIcon(MediaActionDrawable.ICON_DOWNLOAD, true, animated);
                    setUpdateText(LocaleController.getString(R.string.AppUpdateDownloadNow), animated);
                    showSize = true;
                }
            }
            if (showSize) {
                if (updateSizeTextView.getTag() != null) {
                    if (animated) {
                        updateSizeTextView.setTag(null);
                        updateSizeTextView.animate().alpha(1.0f).scaleX(1.0f).scaleY(1.0f).setDuration(180).start();
                    } else {
                        updateSizeTextView.setAlpha(1.0f);
                        updateSizeTextView.setScaleX(1.0f);
                        updateSizeTextView.setScaleY(1.0f);
                    }
                }
            } else {
                if (updateSizeTextView.getTag() == null) {
                    if (animated) {
                        updateSizeTextView.setTag(1);
                        updateSizeTextView.animate().alpha(0.0f).scaleX(0.0f).scaleY(0.0f).setDuration(180).start();
                    } else {
                        updateSizeTextView.setAlpha(0.0f);
                        updateSizeTextView.setScaleX(0.0f);
                        updateSizeTextView.setScaleY(0.0f);
                    }
                }
            }
            if (updateLayout.getTag() != null) {
                return;
            }
            updateLayout.setVisibility(View.VISIBLE);
            updateLayout.setTag(1);
            if (animated) {
                updateLayout.animate().translationY(0).setInterpolator(CubicBezierInterpolator.EASE_OUT).setListener(null).setDuration(180).start();
            } else {
                updateLayout.setTranslationY(0);
            }
            sideMenu.setPadding(0, 0, 0, AndroidUtilities.dp(44));
        } else {
            if (updateLayout == null || updateLayout.getTag() == null) {
                return;
            }
            updateLayout.setTag(null);
            if (animated) {
                updateLayout.animate().translationY(AndroidUtilities.dp(44)).setInterpolator(CubicBezierInterpolator.EASE_OUT).setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (updateLayout.getTag() == null) {
                            updateLayout.setVisibility(View.INVISIBLE);
                        }
                    }
                }).setDuration(180).start();
            } else {
                updateLayout.setTranslationY(AndroidUtilities.dp(44));
                updateLayout.setVisibility(View.INVISIBLE);
            }
            sideMenu.setPadding(0, 0, 0, 0);
        }
    }

    @Override
    public void setOnUpdateLayoutClicked(Runnable onUpdateLayoutClicked) {
        this.onUpdateLayoutClicked = onUpdateLayoutClicked;
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.updateDownloadingStarted) {
            updateAppUpdateViews(UserConfig.selectedAccount, true);
        } else if (id == NotificationCenter.maskedUpdateReceived) {
            updateAppUpdateViews(UserConfig.selectedAccount, true);
        }
    }

    @Override
    public boolean isCancelIcon() {
        return updateLayoutIcon != null && updateLayoutIcon.getIcon() == MediaActionDrawable.ICON_CANCEL;
    }

    private void setUpdateText(String text, boolean animate) {
        if (TextUtils.equals(updateTextViews[0].getText(), text)) {
            return;
        }
        if (updateTextAnimator != null) {
            updateTextAnimator.cancel();
            updateTextAnimator = null;
        }

        if (animate) {
            updateTextViews[1].setText(updateTextViews[0].getText());
            updateTextViews[0].setText(text);

            updateTextViews[0].setAlpha(0);
            updateTextViews[1].setAlpha(1);
            updateTextViews[0].setVisibility(View.VISIBLE);
            updateTextViews[1].setVisibility(View.VISIBLE);

            ArrayList<Animator> arrayList = new ArrayList<>();
            arrayList.add(ObjectAnimator.ofFloat(updateTextViews[1], View.ALPHA, 0));
            arrayList.add(ObjectAnimator.ofFloat(updateTextViews[0], View.ALPHA, 1));

            updateTextAnimator = new AnimatorSet();
            updateTextAnimator.playTogether(arrayList);
            updateTextAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (updateTextAnimator == animation) {
                        updateTextViews[1].setVisibility(View.GONE);
                        updateTextAnimator = null;
                    }
                }
            });
            updateTextAnimator.setDuration(320);
            updateTextAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            updateTextAnimator.start();
        } else {
            updateTextViews[0].setText(text);
            updateTextViews[0].setAlpha(1);
            updateTextViews[0].setVisibility(View.VISIBLE);
            updateTextViews[1].setVisibility(View.GONE);
        }
    }
}
