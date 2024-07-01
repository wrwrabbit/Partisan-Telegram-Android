/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.RemoveChatsAction;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.core.graphics.drawable.DrawableCompat;

import com.google.android.exoplayer2.util.Consumer;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CheckBox2;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.RemoveChatsAction.items.Item;

public class ChatRemoveCell extends FrameLayout {

    private final BackupImageView avatarImageView;
    private final LinearLayout nameLayout;
    private final ImageView lockImageView;
    private final SimpleTextView nameTextView;
    private final SimpleTextView statusTextView;
    private final CheckBox2 checkBox;
    private final AvatarDrawable avatarDrawable;
    private Item item;
    private final ImageView settingsButton;

    private final int currentAccount;

    Consumer<Item> onSettingsClick;

    public ChatRemoveCell(Context context, int account) {
        super(context);

        currentAccount = account;

        avatarDrawable = new AvatarDrawable();

        avatarImageView = new BackupImageView(context);
        avatarImageView.setRoundRadius(AndroidUtilities.dp(24));
        addView(avatarImageView, LayoutHelper.createFrame(46, 46, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 0 : 13, 6, LocaleController.isRTL ? 13 : 0, 0));

        nameLayout = new LinearLayout(context);
        nameLayout.setOrientation(LinearLayout.HORIZONTAL);
        addView(nameLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 20, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 72, 10, 60, 0));

        lockImageView = new ImageView(context);
        lockImageView.setImageDrawable(Theme.dialogs_lockDrawable);
        nameLayout.addView(lockImageView, AndroidUtilities.dp(16), AndroidUtilities.dp(16));

        nameTextView = new SimpleTextView(context);
        nameTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        nameTextView.setTextSize(16);
        nameTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        nameLayout.addView(nameTextView);

        statusTextView = new SimpleTextView(context);
        statusTextView.setTextSize(14);
        statusTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
        addView(statusTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 20, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 72, 32, 60, 0));

        settingsButton = new ImageView(getContext());
        settingsButton.setScaleType(ImageView.ScaleType.CENTER);
        settingsButton.setImageResource(R.drawable.msg_settings_old);
        Drawable drawable = DrawableCompat.wrap(settingsButton.getDrawable());
        DrawableCompat.setTintList(drawable, new ColorStateList(new int[][]{
                {}
        }, new int[] {
                Theme.getColor(Theme.key_dialogFloatingButton),
        }));
        settingsButton.setImageDrawable(drawable);
        settingsButton.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.getColor(Theme.key_actionBarActionModeDefaultSelector)));
        settingsButton.setPadding(AndroidUtilities.dp(1), 0, 0, 0);
        addView(settingsButton, LayoutHelper.createFrame(46, 46, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP, LocaleController.isRTL ? 13 : 0, 6, LocaleController.isRTL ? 0 : 13, 0));
        settingsButton.setOnClickListener(v -> {
            if (onSettingsClick != null) {
                onSettingsClick.accept(item);
            }
        });

        checkBox = new CheckBox2(context, 21);
        checkBox.setColor(-1, Theme.key_windowBackgroundWhite, Theme.key_checkboxCheck);
        checkBox.setDrawUnchecked(false);
        checkBox.setDrawBackgroundAsArc(3);
        addView(checkBox, LayoutHelper.createFrame(24, 24, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 0 : 40, 33, LocaleController.isRTL ? 39 : 0, 0));

        setWillNotDraw(false);
    }

    void setItem(Item item) {
        this.item = item;
        update();
    }

    public void setChecked(boolean checked, boolean animated) {
        settingsButton.setVisibility(checked ? VISIBLE : GONE);
        if (checkBox != null) {
            checkBox.setChecked(checked, animated);
        }
    }

    public void setCheckBoxEnabled(boolean enabled) {
        if (checkBox != null) {
            checkBox.setEnabled(enabled);
        }
    }

    public boolean isChecked() {
        return checkBox.isChecked();
    }

    public void setOnSettingsClick(Consumer<Item> onSettingsClick) {
        this.onSettingsClick = onSettingsClick;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(58), MeasureSpec.EXACTLY));
    }

    public void recycle() {
        avatarImageView.getImageReceiver().cancelLoadImage();
    }

    public void update() {
        updateSizes();
        updateAvatar();
        updateName();
        updateStatus();
    }

    private void updateSizes() {
        if (TextUtils.isEmpty(item.getStatus())) {
            ((LayoutParams) nameLayout.getLayoutParams()).topMargin = AndroidUtilities.dp(19);
        } else {
            ((LayoutParams) nameLayout.getLayoutParams()).topMargin = AndroidUtilities.dp(10);
        }
        avatarImageView.getLayoutParams().width = avatarImageView.getLayoutParams().height = AndroidUtilities.dp(46);
        ((LayoutParams) checkBox.getLayoutParams()).topMargin = AndroidUtilities.dp(33);
        if (LocaleController.isRTL) {
            ((LayoutParams) checkBox.getLayoutParams()).rightMargin = AndroidUtilities.dp(39);
        } else {
            ((LayoutParams) checkBox.getLayoutParams()).leftMargin = AndroidUtilities.dp(40);
        }
    }

    private void updateAvatar() {
        if (item.isSelf()) {
            avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_SAVED);
            avatarImageView.setImage(null, "50_50", avatarDrawable, item.getProfileObject());
        } else if (item.getProfileObject() != null) {
            avatarDrawable.setInfo(currentAccount, item.getProfileObject());
            avatarImageView.setForUserOrChat(item.getProfileObject(), avatarDrawable);
        } else {
            avatarDrawable.setInfo(item.getId(), item.getDisplayName().toString(), "");
            avatarImageView.setForUserOrChat(null, avatarDrawable);
        }
    }

    private void updateName() {
        nameTextView.setText(item.getDisplayName());
        if (DialogObject.isEncryptedDialog(item.getId())) {
            lockImageView.setVisibility(VISIBLE);
            nameTextView.setTextColor(Theme.getColor(Theme.key_chats_secretName));
            nameTextView.setPadding(AndroidUtilities.dp(3), 0, 0, 0);
        } else {
            lockImageView.setVisibility(GONE);
            nameTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            nameTextView.setPadding(0, 0, 0, 0);
        }
    }

    private void updateStatus() {
        statusTextView.setText(item.getStatus(), true);
        statusTextView.setTag(Theme.key_windowBackgroundWhiteGrayText);
        statusTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
    }

    public void setItemSelected(boolean selected) {
        if (selected) {
            setBackgroundColor(Theme.getColor(Theme.key_actionBarActionModeDefaultSelector));
        } else {
            setBackground(null);
        }
    }

    Item getItem() {
        return item;
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    private MessagesController getMessagesController() {
        return MessagesController.getInstance(currentAccount);
    }
}