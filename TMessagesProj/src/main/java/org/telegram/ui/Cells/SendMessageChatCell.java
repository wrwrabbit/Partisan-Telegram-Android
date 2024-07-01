/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.fakepasscode.RemoveChatsAction;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CheckBox2;
import org.telegram.ui.Components.LayoutHelper;

public class SendMessageChatCell extends FrameLayout {

    private BackupImageView avatarImageView;
    private final LinearLayout nameLayout;
    private final ImageView lockImageView;
    private SimpleTextView nameTextView;
    private CheckBox2 checkBox;
    private AvatarDrawable avatarDrawable;
    private Object currentObject;
    private CharSequence currentName;

    private int currentAccount = UserConfig.selectedAccount;

    private String lastName;
    private int lastStatus;

    private boolean drawDivider;

    public SendMessageChatCell(Context context) {
        super(context);

        drawDivider = false;
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

        checkBox = new CheckBox2(context, 21);
        checkBox.setColor(-1, Theme.key_windowBackgroundWhite, Theme.key_checkboxCheck);
        checkBox.setDrawUnchecked(false);
        checkBox.setDrawBackgroundAsArc(3);
        addView(checkBox, LayoutHelper.createFrame(24, 24, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 0 : 40, 33, LocaleController.isRTL ? 39 : 0, 0));

        setWillNotDraw(false);
    }

    public void setObject(Object object, CharSequence name,  boolean drawDivider) {
        currentObject = object;
        currentName = name;
        this.drawDivider = drawDivider;
        update(0);
    }

    public void setChecked(boolean checked, boolean animated) {
        checkBox.setChecked(checked, animated);
    }

    public void setCheckBoxEnabled(boolean enabled) {
        if (checkBox != null) {
            checkBox.setEnabled(enabled);
        }
    }

    public long getDialogId() {
        if (currentObject instanceof TLRPC.User) {
            return ((TLRPC.User) currentObject).id;
        } else if (currentObject instanceof TLRPC.EncryptedChat) {
            return DialogObject.makeEncryptedDialogId(((TLRPC.EncryptedChat) currentObject).id);
        } else if (currentObject instanceof TLRPC.Chat) {
            return -((TLRPC.Chat) currentObject).id;
        } else if (currentObject instanceof Long) {
            return (long) currentObject;
        } else {
            return 0;
        }
    }

    public Object getObject() {
        return currentObject;
    }

    public void setDrawDivider(boolean value) {
        drawDivider = value;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(currentObject instanceof String ? 50 : 58), MeasureSpec.EXACTLY));
    }

    public void recycle() {
        avatarImageView.getImageReceiver().cancelLoadImage();
    }

    public void update(int mask) {
        if (currentObject == null) {
            return;
        }
        String newName = null;

        ((LayoutParams) nameLayout.getLayoutParams()).topMargin = AndroidUtilities.dp(10);
        avatarImageView.getLayoutParams().width = avatarImageView.getLayoutParams().height = AndroidUtilities.dp(46);
        if (checkBox != null) {
            ((LayoutParams) checkBox.getLayoutParams()).topMargin = AndroidUtilities.dp(33);
            if (LocaleController.isRTL) {
                ((LayoutParams) checkBox.getLayoutParams()).rightMargin = AndroidUtilities.dp(39);
            } else {
                ((LayoutParams) checkBox.getLayoutParams()).leftMargin = AndroidUtilities.dp(40);
            }
        }

        if (DialogObject.isEncryptedDialog(getDialogId())) {
            lockImageView.setVisibility(VISIBLE);
            nameTextView.setTextColor(Theme.getColor(Theme.key_chats_secretName));
            nameTextView.setPadding(AndroidUtilities.dp(3), 0, 0, 0);
        } else {
            lockImageView.setVisibility(GONE);
            nameTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            nameTextView.setPadding(0, 0, 0, 0);
        }

        if (currentObject instanceof TLRPC.User) {
            TLRPC.User currentUser = (TLRPC.User) currentObject;
            if (UserObject.isUserSelf(currentUser)) {
                nameTextView.setText(LocaleController.getString("SavedMessages", R.string.SavedMessages), true);
                avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_SAVED);
                avatarImageView.setImage(null, "50_50", avatarDrawable, currentUser);
                ((LayoutParams) nameLayout.getLayoutParams()).topMargin = AndroidUtilities.dp(19);
                return;
            }
            if (mask != 0) {
                boolean continueUpdate = false;
                if ((mask & MessagesController.UPDATE_MASK_STATUS) != 0) {
                    int newStatus = 0;
                    if (currentUser.status != null) {
                        newStatus = currentUser.status.expires;
                    }
                    if (newStatus != lastStatus) {
                        continueUpdate = true;
                    }
                }
                if (!continueUpdate && currentName == null && lastName != null && (mask & MessagesController.UPDATE_MASK_NAME) != 0) {
                    newName = UserObject.getUserName(currentUser, currentAccount);
                    if (!newName.equals(lastName)) {
                        continueUpdate = true;
                    }
                }
                if (!continueUpdate) {
                    return;
                }
            }
            avatarDrawable.setInfo(currentAccount, currentUser);
            lastStatus = currentUser.status != null ? currentUser.status.expires : 0;

            if (currentName != null) {
                lastName = null;
                nameTextView.setText(currentName, true);
            } else {
                lastName = newName == null ? UserObject.getUserName(currentUser, currentAccount) : newName;
                nameTextView.setText(lastName);
            }

            avatarImageView.setForUserOrChat(currentUser, avatarDrawable);
        } else if (currentObject instanceof TLRPC.Chat) {
            TLRPC.Chat currentChat = (TLRPC.Chat) currentObject;
            if (mask != 0) {
                boolean continueUpdate = false;
                if (currentName == null && lastName != null && (mask & MessagesController.UPDATE_MASK_NAME) != 0) {
                    newName = UserConfig.getChatTitleOverride(currentAccount, currentChat);
                    if (newName == null) {
                        newName = currentChat.title;
                    }
                    if (!newName.equals(lastName)) {
                        continueUpdate = true;
                    }
                }
                if (!continueUpdate) {
                    return;
                }
            }

            avatarDrawable.setInfo(currentAccount, currentChat);

            if (currentName != null) {
                lastName = null;
                nameTextView.setText(currentName, true);
            } else {
                String title = UserConfig.getChatTitleOverride(currentAccount, currentChat);
                lastName = newName == null ? title : newName;
                nameTextView.setText(lastName);
            }

            avatarImageView.setForUserOrChat(currentChat, avatarDrawable);
        } else if (currentObject instanceof TLRPC.EncryptedChat) {
            TLRPC.EncryptedChat encryptedChat = (TLRPC.EncryptedChat) currentObject;
            TLRPC.User currentUser = MessagesController.getInstance(currentAccount).getUser(encryptedChat.user_id);
            if (mask != 0) {
                boolean continueUpdate = false;
                if ((mask & MessagesController.UPDATE_MASK_STATUS) != 0) {
                    int newStatus = 0;
                    if (currentUser != null && currentUser.status != null) {
                        newStatus = currentUser.status.expires;
                    }
                    if (newStatus != lastStatus) {
                        continueUpdate = true;
                    }
                }
                if (!continueUpdate && currentName == null && lastName != null && (mask & MessagesController.UPDATE_MASK_NAME) != 0) {
                    newName = UserObject.getUserName(currentUser, currentAccount);
                    if (!newName.equals(lastName)) {
                        continueUpdate = true;
                    }
                }
                if (!continueUpdate) {
                    return;
                }
            }
            avatarDrawable.setInfo(currentAccount, currentUser);
            lastStatus = currentUser != null && currentUser.status != null ? currentUser.status.expires : 0;

            if (currentName != null) {
                lastName = null;
                nameTextView.setText(currentName, true);
            } else {
                lastName = newName == null ? UserObject.getUserName(currentUser, currentAccount) : newName;
                nameTextView.setText(lastName);
            }

            avatarImageView.setForUserOrChat(currentUser, avatarDrawable);
        } else if (currentObject instanceof Long) {
            String name = UserObject.getUserName(null, currentAccount);
            nameTextView.setText(name);
            avatarDrawable.setInfo((long) currentObject, name, "");
            avatarImageView.setForUserOrChat(null, avatarDrawable);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (drawDivider) {
            int start = AndroidUtilities.dp(LocaleController.isRTL ? 0 : 72);
            int end = getMeasuredWidth() - AndroidUtilities.dp(!LocaleController.isRTL ? 0 : 72);
            canvas.drawRect(start, getMeasuredHeight() - 1, end, getMeasuredHeight(), Theme.dividerPaint);
        }
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }
}
