package org.telegram.ui.Cells;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.Canvas;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.partisan.secretgroups.EncryptedGroup;
import org.telegram.messenger.partisan.secretgroups.InnerEncryptedChat;
import org.telegram.messenger.partisan.secretgroups.InnerEncryptedChatState;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;

public class EncryptedGroupMemberCell extends FrameLayout {
    private final SimpleTextView nameTextView;
    private final SimpleTextView statusTextView;
    private final BackupImageView avatarImageView;
    private final AvatarDrawable avatarDrawable;

    private boolean needDivider;
    private EncryptedGroup currentEncryptedGroup;
    private final int currentAccount;

    public EncryptedGroupMemberCell(Context context, EncryptedGroup encryptedGroup, int currentAccount) {
        super(context);

        this.currentEncryptedGroup = encryptedGroup;
        this.currentAccount = currentAccount;

        avatarDrawable = new AvatarDrawable();
        avatarDrawable.setTextSize(AndroidUtilities.dp(18));

        avatarImageView = new BackupImageView(context);
        avatarImageView.setRoundRadius(AndroidUtilities.dp(24));
        addView(avatarImageView, LayoutHelper.createFrame(46, 46, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 0 : 14, 6, LocaleController.isRTL ? 14 : 0, 0));

        nameTextView = new SimpleTextView(context);
        nameTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        nameTextView.setTypeface(AndroidUtilities.bold());
        nameTextView.setTextSize(16);
        nameTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
        addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 20, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 28 : 64, 10, LocaleController.isRTL ? 64 : 28, 0));

        statusTextView = new SimpleTextView(context);
        statusTextView.setTextSize(15);
        statusTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
        addView(statusTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 20, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 28 : 64, 32, LocaleController.isRTL ? 64 : 28, 0));

        setWillNotDraw(false);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(View.MeasureSpec.makeMeasureSpec(View.MeasureSpec.getSize(widthMeasureSpec), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(dp(58) + (needDivider ? 1 : 0), View.MeasureSpec.EXACTLY));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        nameTextView.setTextColor(Theme.getColor(Theme.key_chats_menuItemText));
    }

    public void setDivider(boolean divider) {
        needDivider = divider;
        setWillNotDraw(!divider);
    }

    public void setUserAndInnerChat(TLRPC.User user, InnerEncryptedChat innerChat, boolean divider) {
        if (user == null) {
            nameTextView.setText(LocaleController.getString(R.string.HiddenName));
            statusTextView.setTextColor(Theme.getColor(Theme.key_text_RedRegular));
            statusTextView.setText(LocaleController.getString(R.string.InnerEncryptedChatStateMemberDeleted));
            avatarDrawable.setInfo(innerChat.getUserId(), "?", "");
            avatarImageView.getImageReceiver().setCurrentAccount(currentAccount);
            avatarImageView.setForUserOrChat(null, avatarDrawable);
            needDivider = divider;
            setWillNotDraw(!divider);
            return;
        }
        avatarDrawable.setInfo(user);
        nameTextView.setText(ContactsController.formatName(user.first_name, user.last_name));

        if (currentEncryptedGroup.getOwnerUserId() == user.id) {
            statusTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
            statusTextView.setText(LocaleController.getString(R.string.ChannelCreator));
        } else if (innerChat == null || innerChat.getState() == InnerEncryptedChatState.INITIALIZED) {
            if (user.id == UserConfig.getInstance(currentAccount).getClientUserId() || user.status != null && user.status.expires > ConnectionsManager.getInstance(currentAccount).getCurrentTime() || MessagesController.getInstance(currentAccount).onlinePrivacy.containsKey(user.id)) {
                statusTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
                statusTextView.setText(getString(R.string.Online));
            } else {
                statusTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
                statusTextView.setText(LocaleController.formatUserStatus(currentAccount, user));
            }
        } else {
            if (SharedConfig.detailedEncryptedGroupMemberStatus) {
                statusTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
                statusTextView.setText(innerChat.getState().toString());
            } else {
                switch (innerChat.getState()) {
                    case CREATING_ENCRYPTED_CHAT:
                    case NEED_SEND_INVITATION:
                    case INVITATION_SENT:
                        statusTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
                        statusTextView.setText(LocaleController.getString(R.string.InnerEncryptedChatStateInvitationSent));
                        break;
                    case WAITING_SECONDARY_CHATS_CREATION:
                    case NEED_SEND_SECONDARY_INVITATION:
                        statusTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
                        statusTextView.setText(LocaleController.getString(R.string.InnerEncryptedChatStateInitialization));
                        break;
                    case INITIALIZATION_FAILED:
                        statusTextView.setTextColor(Theme.getColor(Theme.key_text_RedRegular));
                        statusTextView.setText(LocaleController.getString(R.string.InnerEncryptedChatStateInvitationDeclined));
                        break;
                    case CANCELLED:
                        statusTextView.setTextColor(Theme.getColor(Theme.key_text_RedRegular));
                        statusTextView.setText(LocaleController.getString(R.string.InnerEncryptedChatStateMemberDeleted));
                        break;
                }
            }
        }
        avatarImageView.getImageReceiver().setCurrentAccount(currentAccount);
        avatarImageView.setForUserOrChat(user, avatarDrawable);
        needDivider = divider;
        setWillNotDraw(!divider);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (needDivider) {
            canvas.drawLine(LocaleController.isRTL ? 0 : AndroidUtilities.dp(20), getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? AndroidUtilities.dp(20) : 0), getMeasuredHeight() - 1, Theme.dividerPaint);
        }
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.addAction(AccessibilityNodeInfo.ACTION_CLICK);
    }
}
