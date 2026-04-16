/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.text.style.ReplacementSpan;
import android.text.style.StyleSpan;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ChatThemeController;
import org.telegram.messenger.CodeHighlighting;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.DownloadController;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stories;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Adapters.SavedChannelsAdapter;
import org.telegram.ui.AvatarSpan;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.CanvasButton;
import org.telegram.ui.Components.CheckBox2;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.DialogCellTags;
import org.telegram.ui.Components.ForegroundColorSpanThemable;
import org.telegram.ui.Components.Forum.ForumBubbleDrawable;
import org.telegram.ui.Components.Forum.ForumUtilities;
import org.telegram.ui.Components.PhotoBubbleClip;
import org.telegram.ui.Components.Premium.PremiumGradient;
import org.telegram.ui.Components.QuoteSpan;
import org.telegram.ui.Components.StaticLayoutEx;
import org.telegram.ui.Components.StatusDrawable;
import org.telegram.ui.Components.TextStyleSpan;
import org.telegram.ui.Components.TimerDrawable;
import org.telegram.ui.Components.TypefaceSpan;
import org.telegram.ui.Components.URLSpanNoUnderline;
import org.telegram.ui.Components.URLSpanNoUnderlineBold;
import org.telegram.ui.Components.VectorAvatarThumbDrawable;
import org.telegram.ui.Components.spoilers.SpoilerEffect;
import org.telegram.ui.SavedChannelsActivity;
import org.telegram.ui.FilterCreateActivity;
import org.telegram.ui.RightSlidingDialogContainer;
import org.telegram.ui.Stars.StarsIntroActivity;
import org.telegram.ui.Stories.StoriesListPlaceProvider;
import org.telegram.ui.Stories.StoriesUtilities;
import org.telegram.ui.Stories.StoryViewer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Stack;

public class SavedChannelCell extends BaseCell implements StoriesListPlaceProvider.AvatarOverlaysView {

    public boolean collapsed;
    public float rightFragmentOffset;
    public static final int SENT_STATE_NOTHING = -1;
    public static final int SENT_STATE_READ = 2;
    public boolean drawAvatar = true;
    public boolean drawMonoforumAvatar = false;
    public int avatarStart = 11;
    public int messagePaddingStart = 72;
    public int heightDefault = 70;
    public int heightThreeLines = 76;
    public int addHeightForTags = 3;
    public int addForumHeightForTags = 11;
    public TLRPC.TL_forumTopic forumTopic;
    public boolean useFromUserAsAvatar;
    public float chekBoxPaddingTop = 42;
    private boolean needEmoji;
    private boolean hasNameInMessage;
    private TextPaint currentMessagePaint;
    private Paint buttonBackgroundPaint;
    CanvasButton canvasButton;
    SavedChannelCellDelegate delegate;
    private boolean applyName;
    private boolean lastTopicMessageUnread;
    protected Drawable topicIconInName[];
    public boolean isTransitionSupport;
    public boolean drawAvatarSelector;
    public boolean inPreviewMode;
    private boolean buttonCreated;
    private int ttlPeriod;
    private float ttlProgress;
    private TimerDrawable timerDrawable;
    private Paint timerPaint;
    private Paint timerPaint2;
    public boolean isMonoForumTopicDialog;
    public DialogCellTags tags;

    public final StoriesUtilities.AvatarStoryParams storyParams = new StoriesUtilities.AvatarStoryParams(false) {
        @Override
        public void openStory(long dialogId, Runnable onDone) {
            if (delegate == null) {
                return;
            }
            if (delegate != null) {
                delegate.openStory(SavedChannelCell.this, onDone);
            }
        }

        @Override
        public void onLongPress() {
            if (delegate == null) {
                return;
            }
            delegate.showChatPreview(SavedChannelCell.this);
        }
    };

    private Path thumbPath;
    private SpoilerEffect thumbSpoiler;
    private boolean drawForwardIcon, drawGiftIcon;
    private boolean visibleOnScreen = true;
    private boolean updateLayout;

    public void setIsTransitionSupport(boolean isTransitionSupport) {
        this.isTransitionSupport = isTransitionSupport;
    }

    public float collapseOffset = 0;

    public void checkHeight() {
        if (getMeasuredHeight() > 0 && getMeasuredHeight() != computeHeight()) {
            requestLayout();
        }
    }

    public static class FixedWidthSpan extends ReplacementSpan {

        private int width;

        public FixedWidthSpan(int w) {
            width = w;
        }

        @Override
        public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
            if (fm == null) {
                fm = paint.getFontMetricsInt();
            }
            if (fm != null) {
                int h = fm.descent - fm.ascent;
                fm.bottom = fm.descent = 1 - h;
                fm.top = fm.ascent = -1;
            }
            return width;
        }

        @Override
        public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, Paint paint) {

        }
    }

    private int paintIndex;

    private int currentAccount;
    private long currentDialogId;
    private String titleOverride;
    public boolean isDialogCell;
    private int lastMessageDate;
    private boolean lastUnreadState;
    private int lastSendState;
    private boolean dialogMuted;
    private boolean topicMuted;
    private boolean drawUnmute;
    private float dialogMutedProgress;
    private boolean hasUnmutedTopics = false;
    private MessageObject message;
    private ArrayList<MessageObject> groupMessages;
    private CharSequence lastMessageString;
    private int messageId;
    protected boolean forbidVerified;

    private float clipProgress;
    private int topClip;
    private int bottomClip;
    private boolean drawRevealBackground;
    private float currentRevealProgress;
    private float currentRevealBounceProgress;

    private int thumbsCount;
    private boolean hasVideoThumb;
    private Paint thumbBackgroundPaint;
    private boolean[] thumbImageSeen = new boolean[3];
    private ImageReceiver[] thumbImage = new ImageReceiver[3];
    private boolean[] drawPlay = new boolean[3];
    private boolean[] drawSpoiler = new boolean[3];

    public ImageReceiver avatarImage = new ImageReceiver(this);
    private PhotoBubbleClip bubbleClip;
    private AvatarDrawable avatarDrawable = new AvatarDrawable();

    private TLRPC.Chat chat;
    private CharSequence lastPrintString;
    private int printingStringType;

    protected CheckBox2 checkBox;

    public boolean useForceThreeLines;
    public boolean useSeparator;

    private boolean showTtl;

    public boolean isForChannelSubscriberCell;
    public int namePaddingEnd;
    public int nameLeft;
    public int nameWidth;
    public int nameAdditionalsForChannelSubscriber;
    public boolean channelShouldUseLineWidth;
    public StaticLayout nameLayout;
    private boolean nameLayoutFits;
    public float nameLayoutTranslateX;
    private boolean nameLayoutEllipsizeLeft;
    private boolean nameLayoutEllipsizeByGradient;
    private Paint fadePaint;
    private Paint fadePaintBack;
    private int nameMuteLeft;

    private int timeLeft;
    private int timeTop;
    private StaticLayout timeLayout;

    private int lock2Left;

    private int tagsLeft, tagsRight;
    private int messageTop;
    private int messageLeft;
    private int buttonLeft;
    private int typingLeft;
    private StaticLayout messageLayout;
    private StaticLayout typingLayout;

    private int buttonTop;
    private StaticLayout buttonLayout;

    private Stack<SpoilerEffect> spoilersPool = new Stack<>();
    private List<SpoilerEffect> spoilers = new ArrayList<>();
    private Stack<SpoilerEffect> spoilersPool2 = new Stack<>();
    private List<SpoilerEffect> spoilers2 = new ArrayList<>();
    private AnimatedEmojiSpan.EmojiGroupedSpans animatedEmojiStack, animatedEmojiStack2, animatedEmojiStack3, animatedEmojiStackName;

    private int messageNameTop;
    private int messageNameLeft;
    private StaticLayout messageNameLayout;

    private boolean attachedToWindow;

    private float reorderIconProgress;
    private boolean drawReorder;
    private boolean drawPinBackground;
    private boolean drawPin;
    private boolean drawPinForced;
    private int pinTop;
    private int pinLeft;
    protected int translateY;
    protected float xOffset;

    private boolean drawVerified;
    private boolean drawBotVerified;
    private boolean drawPremium;
    private final View emojiStatusView;
    private final AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable emojiStatus;
    private final AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable botVerification;

    private int drawScam;

    private boolean isSelected;

    private RectF rect = new RectF();
    private SavedChannelsAdapter.DialogsPreloader preloader;

    long lastDialogChangedTime;

    private SavedChannelsActivity parentFragment;


    private final DialogUpdateHelper updateHelper = new DialogUpdateHelper();

    private final Theme.ResourcesProvider resourcesProvider;

    public SavedChannelCell(SavedChannelsActivity fragment, Context context, boolean needCheck, boolean forceThreeLines, int account, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        storyParams.allowLongress = true;
        this.resourcesProvider = resourcesProvider;
        parentFragment = fragment;
        Theme.createDialogsResources(context);
        drawMonoforumAvatar = false;
        avatarImage.setRoundRadius(dp(28));
        for (int i = 0; i < thumbImage.length; ++i) {
            thumbImage[i] = new ImageReceiver(this);
            thumbImage[i].ignoreNotifications = true;
            thumbImage[i].setRoundRadius(dp(2));
            thumbImage[i].setAllowLoadingOnAttachedOnly(true);
        }
        useForceThreeLines = forceThreeLines;
        currentAccount = account;

        emojiStatusView = new View(context) {
            @Override
            protected void onDraw(@NonNull Canvas canvas) {
                emojiStatus.setBounds(0, 0, getWidth(), getHeight());
                emojiStatus.draw(canvas);
            }
        };
        addView(emojiStatusView);
        emojiStatus = new AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable(emojiStatusView, dp(22));
        botVerification = new AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable(this, dp(17));
        avatarImage.setAllowLoadingOnAttachedOnly(true);
    }

    @Override
    public void requestLayout() {
        if (getParent() != null && getParent().isLayoutRequested()) {
            getParent().requestLayout();
        }
        super.requestLayout();
    }

    public void setChat(TLRPC.Chat chat, boolean pinned, ArrayList<MessageObject> groupMessages) {
        long newDialogId = -chat.id;
        currentDialogId = newDialogId;
        this.groupMessages = groupMessages;
        MessageObject topMessage = groupMessages != null && !groupMessages.isEmpty() ? groupMessages.get(0) : null;
        message = topMessage;
        this.drawPin = pinned;
        lastDialogChangedTime = System.currentTimeMillis();
        isDialogCell = true;
        if (tags == null) {
            tags = new DialogCellTags(this);
        }
        messageId = topMessage != null ? topMessage.getId() : 0;
        if (update(0, false)) {
            requestLayout();
        }
        checkChatTheme();
        checkTtl();
    }

    protected boolean drawLock2() {
        return false;
    }

    private void checkTtl() {
        showTtl = ttlPeriod > 0 && !(checkBox != null && checkBox.isChecked()) && !storyParams.drawnLive;
        ttlProgress = showTtl ? 1.0f : 0.0f;
    }

    private void checkChatTheme() {
        if (message != null && message.messageOwner != null && message.messageOwner.action instanceof TLRPC.TL_messageActionSetChatTheme && lastUnreadState) {
            TLRPC.TL_messageActionSetChatTheme setThemeAction = (TLRPC.TL_messageActionSetChatTheme) message.messageOwner.action;
            ChatThemeController.getInstance(currentAccount).setDialogTheme(currentDialogId, setThemeAction.theme, false);
        }
    }

    public long getDialogId() {
        return currentDialogId;
    }

    public String getUsername() {
        return chat != null ? chat.username : null;
    }

    public int getMessageId() {
        return messageId;
    }

    public void setPreloader(SavedChannelsAdapter.DialogsPreloader preloader) {
        this.preloader = preloader;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        drawRevealBackground = false;
        currentRevealProgress = 0.0f;
        attachedToWindow = false;
        reorderIconProgress = getIsPinned() && drawReorder ? 1.0f : 0.0f;
        avatarImage.onDetachedFromWindow();
        for (int i = 0; i < thumbImage.length; ++i) {
            thumbImage[i].onDetachedFromWindow();
        }
        if (preloader != null) {
            preloader.remove(currentDialogId);
        }
        if (emojiStatus != null) {
            emojiStatus.detach();
        }
        if (botVerification != null) {
            botVerification.detach();
        }
        AnimatedEmojiSpan.release(this, animatedEmojiStack);
        AnimatedEmojiSpan.release(this, animatedEmojiStack2);
        AnimatedEmojiSpan.release(this, animatedEmojiStack3);
        AnimatedEmojiSpan.release(this, animatedEmojiStackName);
        storyParams.onDetachFromWindow();
        canvasButton = null;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        avatarImage.onAttachedToWindow();
        for (int i = 0; i < thumbImage.length; ++i) {
            thumbImage[i].onAttachedToWindow();
        }
        attachedToWindow = true;
        animatedEmojiStack = AnimatedEmojiSpan.update(AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, this, animatedEmojiStack, messageLayout);
        animatedEmojiStack2 = AnimatedEmojiSpan.update(AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, this, animatedEmojiStack2, messageNameLayout);
        animatedEmojiStack3 = AnimatedEmojiSpan.update(AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, this, animatedEmojiStack3, buttonLayout);
        animatedEmojiStackName =  AnimatedEmojiSpan.update(AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, this, animatedEmojiStackName, nameLayout);
        if (emojiStatus != null) {
            emojiStatus.attach();
        }
        if (botVerification != null) {
            botVerification.attach();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (emojiStatusView != null) {
            emojiStatusView.measure(
                MeasureSpec.makeMeasureSpec(dp(22), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(dp(22), MeasureSpec.EXACTLY)
            );
        }
        if (checkBox != null) {
            checkBox.measure(
                MeasureSpec.makeMeasureSpec(dp(24), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(dp(24), MeasureSpec.EXACTLY)
            );
        }

        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), computeHeight());
        topClip = 0;
        bottomClip = getMeasuredHeight();
    }

    private int computeHeight() {
        int height;
        if (isForumCell() && !isTransitionSupport && !collapsed) {
            height = dp(useForceThreeLines || SharedConfig.useThreeLinesLayout ? 86 : 91);
            if (useSeparator) {
                height += 1;
            }
            if (hasTags()) {
                height += dp(addForumHeightForTags);
            }
        } else {
            height = getCollapsedHeight();
        }
        return height;
    }

    private int getCollapsedHeight() {
        int height = dp(useForceThreeLines || SharedConfig.useThreeLinesLayout ? heightThreeLines : heightDefault);
        if (useSeparator || true) {
            height += 1;
        }
        if (hasTags() && (!(useForceThreeLines || SharedConfig.useThreeLinesLayout) || isForumCell())) {
            height += dp(isForumCell() ? addForumHeightForTags : addHeightForTags);
        }
        return height;
    }

    int lastSize;
    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (currentDialogId == 0) {
            return;
        }
        if (emojiStatusView != null) {
            emojiStatusView.layout(0, 0, dp(22), dp(22));
        }
        if (checkBox != null) {
            int paddingStart = dp(messagePaddingStart - (useForceThreeLines || SharedConfig.useThreeLinesLayout ? 29 : 27));
            int x, y;
            if (inPreviewMode) {
                x = dp(8);//LocaleController.isRTL ? (right - left) - paddingStart : paddingStart;
                y = (getMeasuredHeight() - checkBox.getMeasuredHeight()) >> 1;
            } else {
                x = LocaleController.isRTL ? (right - left) - paddingStart : paddingStart;
                y = dp(chekBoxPaddingTop + (useForceThreeLines || SharedConfig.useThreeLinesLayout ? 6 : 0));
            }
            checkBox.layout(x, y, x + checkBox.getMeasuredWidth(), y + checkBox.getMeasuredHeight());
        }
        int size = getMeasuredHeight() + getMeasuredWidth() << 16;
        if (size != lastSize || updateLayout) {
            updateLayout = false;
            lastSize = size;
            try {
                buildLayout();
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
    }

    public boolean getIsMuted() {
        return dialogMuted;
    }

    public boolean getIsPinned() {
        return drawPin || drawPinForced;
    }

    public void setPinForced(boolean value) {
        drawPinForced = value;
        if (getMeasuredWidth() > 0 && getMeasuredHeight() > 0) {
            buildLayout();
        }
        invalidate();
    }

    public boolean hasTags() {
        return tags != null && !tags.isEmpty();
    }

    int thumbSize;


    // BADGE_SIZE = BADGE_TEXT_PADDING * 2 + BADGE_TEXT_MIN_WIDTH;
    private static final float BADGE_SIZE = 20.666f;
    private static final float BADGE_TEXT_PADDING = 6.333f;
    private static final float BADGE_TEXT_MIN_WIDTH = 8f;
    private static final float BADGE_GAP = 25 - BADGE_TEXT_MIN_WIDTH;
    private static final float BADGE_MARGIN = 15.666f;


    private static final float BADGE_DRAWABLE_SIZE = 16;
    private static final float BADGE_DRAWABLE_OFFSET = (BADGE_SIZE - BADGE_DRAWABLE_SIZE) / 2f;

    public void buildLayout() {
        if (isTransitionSupport) {
            return;
        }
        if (isDialogCell) {
            boolean needUpdate = updateHelper.update();
            if (!needUpdate) {
                return;
            }
        }

        if (useForceThreeLines || SharedConfig.useThreeLinesLayout || true) {
            Theme.dialogs_namePaint[0].setTextSize(dp(17));
            Theme.dialogs_nameEncryptedPaint[0].setTextSize(dp(17));
            Theme.dialogs_messagePaint[0].setTextSize(dp(16));
            Theme.dialogs_messagePrintingPaint[0].setTextSize(dp(16));

            Theme.dialogs_namePaint[1].setTextSize(dp(16));
            Theme.dialogs_nameEncryptedPaint[1].setTextSize(dp(16));
            Theme.dialogs_messagePaint[1].setTextSize(dp(15));
            Theme.dialogs_messagePrintingPaint[1].setTextSize(dp(15));

            Theme.dialogs_messagePaint[1].setColor(Theme.dialogs_messagePaint[1].linkColor = Theme.getColor(Theme.key_chats_message_threeLines, resourcesProvider));
            paintIndex = 1;
            thumbSize = 18;
        } else {
            Theme.dialogs_namePaint[0].setTextSize(dp(17));
            Theme.dialogs_nameEncryptedPaint[0].setTextSize(dp(17));
            Theme.dialogs_messagePaint[0].setTextSize(dp(16));
            Theme.dialogs_messagePrintingPaint[0].setTextSize(dp(16));

            Theme.dialogs_messagePaint[0].setColor(Theme.dialogs_messagePaint[0].linkColor = Theme.getColor(Theme.key_chats_message, resourcesProvider));
            paintIndex = 0;
            thumbSize = 19;
        }

        CharSequence nameString = "";
        String timeString = "";
        CharSequence messageString = "";
        CharSequence typingString = "";
        CharSequence messageNameString = null;
        CharSequence printingString = null;
        CharSequence buttonString = null;
        if (!isForumCell() && isDialogCell) {
            printingString = MessagesController.getInstance(currentAccount).getPrintingString(currentDialogId, getTopicId(), true);
        }
        currentMessagePaint = Theme.dialogs_messagePaint[paintIndex];
        boolean checkMessage = true;

        drawVerified = false;
        drawBotVerified = false;
        drawPremium = false;
        drawForwardIcon = false;
        drawGiftIcon = false;
        drawScam = 0;
        drawPinBackground = false;
        thumbsCount = 0;
        hasVideoThumb = false;
        nameLayoutEllipsizeByGradient = false;
        boolean drawTime = true;
        printingStringType = -1;
        int printingStringReplaceIndex = -1;
        if (!isForumCell()) {
            buttonLayout = null;
        }

        int messageFormatType;
        if ((!useForceThreeLines && !SharedConfig.useThreeLinesLayout) || isForumCell() || hasTags()) {
            //1 - "%2$s: \u2068%1$s\u2069";
            messageFormatType = 1;
            hasNameInMessage = true;
        } else {
            //2 - "\u2068%1$s\u2069";
            messageFormatType = 2;
            hasNameInMessage = false;
        }

        if (message != null) {
            message.updateTranslation();
        }
        CharSequence msgText = message != null ? message.messageText : null;
        if (msgText instanceof Spannable) {
            Spannable sp = new SpannableStringBuilder(msgText);
            for (Object span : sp.getSpans(0, sp.length(), URLSpanNoUnderlineBold.class))
                sp.removeSpan(span);
            for (Object span : sp.getSpans(0, sp.length(), URLSpanNoUnderline.class))
                sp.removeSpan(span);
            msgText = sp;
        }
        lastMessageString = msgText;

        if (useForceThreeLines || SharedConfig.useThreeLinesLayout) {
            if (!LocaleController.isRTL) {
                nameLeft = dp(messagePaddingStart + 6);
            } else {
                nameLeft = dp(22);
            }
        } else {
            if (!LocaleController.isRTL) {
                nameLeft = dp(messagePaddingStart + 4);
            } else {
                nameLeft = dp(18);
            }
        }

        if (isForumCell()) {
            needEmoji = true;
            updateMessageThumbs();
            messageNameString = ChatObject.isMonoForum(chat) ? null : AndroidUtilities.escape(getMessageNameString());
            if (ChatObject.isMonoForum(chat)) {
                messageNameString = null;
                if (messageFormatType == 1) {
                    messageFormatType = 2;
                }
                if (messageFormatType == 3) {
                    messageFormatType = 4;
                }
            }
            messageString = formatTopicsNames();
            String restrictionReason = message != null ? MessagesController.getInstance(message.currentAccount).getRestrictionReason(message.messageOwner.restriction_reason) : null;
            buttonString = message != null ? getMessageStringFormatted(messageFormatType, restrictionReason, messageNameString, true) : "";
            if (applyName && buttonString.length() >= 0 && messageNameString != null) {
                SpannableStringBuilder spannableStringBuilder = SpannableStringBuilder.valueOf(buttonString);
                spannableStringBuilder.setSpan(new ForegroundColorSpanThemable(Theme.key_chats_name, resourcesProvider), 0, Math.min(spannableStringBuilder.length(), messageNameString.length() + 1), 0);
                buttonString = spannableStringBuilder;
            }
            currentMessagePaint = Theme.dialogs_messagePaint[paintIndex];
        } else {
            if (printingString != null) {
                lastPrintString = printingString;
                printingStringType = MessagesController.getInstance(currentAccount).getPrintingStringType(currentDialogId, getTopicId());
                StatusDrawable statusDrawable = Theme.getChatStatusDrawable(printingStringType);
                int startPadding = 0;
                if (statusDrawable != null) {
                    startPadding = statusDrawable.getIntrinsicWidth() + dp(3);
                }
                SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder();

                printingString = TextUtils.replace(printingString, new String[]{"..."}, new String[]{""});
                if (printingStringType == 5) {
                    printingStringReplaceIndex = printingString.toString().indexOf("**oo**");
                }
                if (printingStringReplaceIndex >= 0) {
                    spannableStringBuilder.append(printingString).setSpan(new FixedWidthSpan(Theme.getChatStatusDrawable(printingStringType).getIntrinsicWidth()), printingStringReplaceIndex, printingStringReplaceIndex + 6, 0);
                } else {
                    spannableStringBuilder.append(" ").append(printingString).setSpan(new FixedWidthSpan(startPadding), 0, 1, 0);
                }

                typingString = spannableStringBuilder;
                checkMessage = false;
            } else {
                lastPrintString = null;
                printingStringType = -1;
            }
            if (message == null) {
                messageString = "";
            } else {
                String restrictionReason = MessagesController.getInstance(message.currentAccount).getRestrictionReason(message.messageOwner.restriction_reason);
                TLRPC.User fromUser = null;
                TLRPC.Chat fromChat = null;
                long fromId = message.getFromChatId();
                if (DialogObject.isUserDialog(fromId)) {
                    fromUser = MessagesController.getInstance(currentAccount).getUser(fromId);
                } else {
                    fromChat = MessagesController.getInstance(currentAccount).getChat(-fromId);
                }
                if (message.messageOwner instanceof TLRPC.TL_messageService && (!MessageObject.isTopicActionMessage(message) || message.messageOwner.action instanceof TLRPC.TL_messageActionTopicCreate)) {
                    if (ChatObject.isChannelAndNotMegaGroup(chat) && (message.messageOwner.action instanceof TLRPC.TL_messageActionChannelMigrateFrom)) {
                        messageString = "";
                    } else if (message.messageTextShort != null) {
                        messageString = message.messageTextShort;
                    } else {
                        messageString = msgText;
                    }
                    currentMessagePaint = Theme.dialogs_messagePrintingPaint[paintIndex];
                    if (message.type == MessageObject.TYPE_SUGGEST_PHOTO) {
                        updateMessageThumbs();
                        messageString = applyThumbs(messageString);
                    }
                } else {
                    needEmoji = true;
                    updateMessageThumbs();
                    String triedMessageName = null;
                    if (
                        triedMessageName != null ||
                        chat != null && chat.id > 0 && (fromChat == null || fromChat.id != chat.id) && (!ChatObject.isChannel(chat) || ChatObject.isMegagroup(chat)) && !ForumUtilities.isTopicCreateMessage(message)
                    ) {
                        messageNameString = AndroidUtilities.escape(triedMessageName != null ? triedMessageName : getMessageNameString());
                        if (chat != null && chat.forum && !useFromUserAsAvatar) {
                            CharSequence topicName = MessagesController.getInstance(currentAccount).getTopicsController().getTopicIconName(chat, message, currentMessagePaint);
                            if (!TextUtils.isEmpty(topicName)) {
                                SpannableStringBuilder arrowSpan = new SpannableStringBuilder("-");
                                ColoredImageSpan coloredImageSpan = new ColoredImageSpan(ContextCompat.getDrawable(ApplicationLoader.applicationContext, R.drawable.msg_mini_forumarrow).mutate());
                                coloredImageSpan.setColorKey(useForceThreeLines || SharedConfig.useThreeLinesLayout ? -1 : Theme.key_chats_nameMessage);
                                arrowSpan.setSpan(coloredImageSpan, 0, 1, 0);
                                SpannableStringBuilder nameSpannableString = new SpannableStringBuilder();
                                nameSpannableString.append(messageNameString).append(arrowSpan).append(topicName);
                                messageNameString = nameSpannableString;
                            }
                        }
                        checkMessage = false;
                        SpannableStringBuilder stringBuilder = getMessageStringFormatted(messageFormatType, restrictionReason, messageNameString, false);

                        int thumbInsertIndex = 0;
                        if (!useFromUserAsAvatar && (!useForceThreeLines && !SharedConfig.useThreeLinesLayout)) {
                            try {
                                stringBuilder.setSpan(new ForegroundColorSpanThemable(Theme.key_chats_nameMessage, resourcesProvider), 0, thumbInsertIndex = messageNameString.length() + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                        }
                        messageString = Emoji.replaceEmoji(stringBuilder, Theme.dialogs_messagePaint[paintIndex].getFontMetricsInt(), false);
                        if (message.hasHighlightedWords()) {
                            CharSequence messageH = AndroidUtilities.highlightText(messageString, message.highlightedWords, resourcesProvider);
                            if (messageH != null) {
                                messageString = messageH;
                            }
                        }
                        if (thumbsCount > 0) {
                            if (!(messageString instanceof SpannableStringBuilder)) {
                                messageString = new SpannableStringBuilder(messageString);
                            }
                            checkMessage = false;
                            SpannableStringBuilder builder = (SpannableStringBuilder) messageString;
                            if (thumbInsertIndex >= builder.length()) {
                                builder.append(" ");
                                builder.setSpan(new FixedWidthSpan(dp(thumbsCount * (thumbSize + 2) - 2 + 5)), builder.length() - 1, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            } else {
                                builder.insert(thumbInsertIndex, " ");
                                builder.setSpan(new FixedWidthSpan(dp(thumbsCount * (thumbSize + 2) - 2 + 5)), thumbInsertIndex, thumbInsertIndex + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            }
                        }
                    } else {
                        if (!TextUtils.isEmpty(restrictionReason)) {
                            messageString = restrictionReason;
                        } else if (MessageObject.isTopicActionMessage(message)) {
                            if (message.messageTextShort != null) {
                                messageString = message.messageTextShort;
                            } else {
                                messageString = message.messageText;
                            }
                            if (message.topicIconDrawable[0] instanceof ForumBubbleDrawable) {
                                TLRPC.TL_forumTopic topic = MessagesController.getInstance(currentAccount).getTopicsController().findTopic(-message.getDialogId(), MessageObject.getTopicId(currentAccount, message.messageOwner, true));
                                if (topic != null) {
                                    ((ForumBubbleDrawable) message.topicIconDrawable[0]).setColor(topic.icon_color);
                                }
                            }
                        } else if (message.messageOwner.media instanceof TLRPC.TL_messageMediaPhoto && message.messageOwner.media.photo instanceof TLRPC.TL_photoEmpty && message.messageOwner.media.ttl_seconds != 0) {
                            messageString = getString(R.string.AttachPhotoExpired);
                        } else if (message.messageOwner.media instanceof TLRPC.TL_messageMediaDocument && (message.messageOwner.media.document instanceof TLRPC.TL_documentEmpty || message.messageOwner.media.document == null) && message.messageOwner.media.ttl_seconds != 0) {
                            if (message.messageOwner.media.voice) {
                                messageString = getString(R.string.AttachVoiceExpired);
                            } else if (message.messageOwner.media.round) {
                                messageString = getString(R.string.AttachRoundExpired);
                            } else {
                                messageString = getString(R.string.AttachVideoExpired);
                            }
                        } else if (getCaptionMessage() != null) {
                            MessageObject message = getCaptionMessage();
                            String emoji;
                            if (!needEmoji) {
                                emoji = "";
                            } else if (message.isVideo()) {
                                emoji = "\uD83D\uDCF9 ";
                            } else if (message.isVoice()) {
                                emoji = "\uD83C\uDFA4 ";
                            } else if (message.isMusic()) {
                                emoji = "\uD83C\uDFA7 ";
                            } else if (message.isPhoto()) {
                                emoji = "\uD83D\uDDBC ";
                            } else {
                                emoji = "\uD83D\uDCCE ";
                            }
                            if (message.hasHighlightedWords() && !TextUtils.isEmpty(message.messageOwner.message)) {
                                CharSequence text = message.messageTrimmedToHighlight;
                                int w = getMeasuredWidth() - dp(messagePaddingStart + 23 + 24);
                                if (hasNameInMessage) {
                                    if (!TextUtils.isEmpty(messageNameString)) {
                                        w -= currentMessagePaint.measureText(messageNameString.toString());
                                    }
                                    w -= currentMessagePaint.measureText(": ");
                                }
                                if (w > 0 && message.messageTrimmedToHighlightCut) {
                                    text = AndroidUtilities.ellipsizeCenterEnd(text, message.highlightedWords.get(0), w, currentMessagePaint, 130).toString();
                                }
                                messageString = new SpannableStringBuilder(emoji).append(text);
                            } else {
                                SpannableString msgBuilder = new SpannableString(message.caption);
                                if (message != null && message.messageOwner != null) {
                                    if (message != null) {
                                        message.spoilLoginCode();
                                    }
                                    MediaDataController.addTextStyleRuns(message.messageOwner.entities, message.caption, msgBuilder, TextStyleSpan.FLAG_STYLE_SPOILER | TextStyleSpan.FLAG_STYLE_STRIKE);
                                    MediaDataController.addAnimatedEmojiSpans(message.messageOwner.entities, msgBuilder, currentMessagePaint == null ? null : currentMessagePaint.getFontMetricsInt());
                                }
                                messageString = new SpannableStringBuilder(emoji).append(msgBuilder);
                            }
                        } else if (message.messageOwner.media instanceof TLRPC.TL_messageMediaPaidMedia) {
                            TLRPC.TL_messageMediaPaidMedia paidMedia = (TLRPC.TL_messageMediaPaidMedia) message.messageOwner.media;
                            final int count = paidMedia.extended_media.size();
                            if (hasVideoThumb) {
                                messageString = count > 1 ? LocaleController.formatPluralString("Media", count) : getString(R.string.AttachVideo);
                            } else {
                                messageString = count > 1 ? LocaleController.formatPluralString("Photos", count) : getString(R.string.AttachPhoto);
                            }
                            messageString = StarsIntroActivity.replaceStars(LocaleController.formatString(R.string.AttachPaidMedia, messageString));
                            currentMessagePaint = Theme.dialogs_messagePrintingPaint[paintIndex];
                        } else if (thumbsCount > 1) {
                            if (hasVideoThumb) {
                                messageString = LocaleController.formatPluralString("Media", groupMessages == null ? 0 : groupMessages.size());
                            } else {
                                messageString = LocaleController.formatPluralString("Photos", groupMessages == null ? 0 : groupMessages.size());
                            }
                            currentMessagePaint = Theme.dialogs_messagePrintingPaint[paintIndex];
                        } else {
                            if (message.messageOwner.media instanceof TLRPC.TL_messageMediaGiveaway) {
                                boolean isChannel;
                                if (message.messageOwner.fwd_from != null && message.messageOwner.fwd_from.from_id instanceof TLRPC.TL_peerChannel) {
                                    isChannel = ChatObject.isChannelAndNotMegaGroup(message.messageOwner.fwd_from.from_id.channel_id, currentAccount);
                                } else {
                                    isChannel = ChatObject.isChannelAndNotMegaGroup(chat);
                                }
                                messageString = getString(isChannel ? R.string.BoostingGiveawayChannelStarted : R.string.BoostingGiveawayGroupStarted);
                            } else if (message.messageOwner.media instanceof TLRPC.TL_messageMediaGiveawayResults) {
                                messageString = getString(R.string.BoostingGiveawayResults);
                            } else if (message.messageOwner.media instanceof TLRPC.TL_messageMediaPoll) {
                                TLRPC.TL_messageMediaPoll mediaPoll = (TLRPC.TL_messageMediaPoll) message.messageOwner.media;
                                if (mediaPoll.poll.question != null && mediaPoll.poll.question.entities != null) {
                                    SpannableString questionText = new SpannableString(mediaPoll.poll.question.text);
                                    MediaDataController.addTextStyleRuns(mediaPoll.poll.question.entities, mediaPoll.poll.question.text, questionText);
                                    MediaDataController.addAnimatedEmojiSpans(mediaPoll.poll.question.entities, questionText, Theme.dialogs_messagePaint[paintIndex].getFontMetricsInt());
                                    messageString = new SpannableStringBuilder("\uD83D\uDCCA ").append(questionText);
                                } else {
                                    messageString = "\uD83D\uDCCA " + mediaPoll.poll.question.text;
                                }
                            } else if (message.messageOwner.media instanceof TLRPC.TL_messageMediaToDo) {
                                TLRPC.TL_messageMediaToDo mediaToDo = (TLRPC.TL_messageMediaToDo) message.messageOwner.media;
                                if (mediaToDo.todo.title != null && mediaToDo.todo.title.entities != null) {
                                    SpannableString questionText = new SpannableString(mediaToDo.todo.title.text);
                                    MediaDataController.addTextStyleRuns(mediaToDo.todo.title.entities, mediaToDo.todo.title.text, questionText);
                                    MediaDataController.addAnimatedEmojiSpans(mediaToDo.todo.title.entities, questionText, Theme.dialogs_messagePaint[paintIndex].getFontMetricsInt());
                                    messageString = new SpannableStringBuilder("\u2705 ").append(questionText);
                                } else {
                                    messageString = "\u2705 " + mediaToDo.todo.title.text;
                                }
                            } else if (message.messageOwner.media instanceof TLRPC.TL_messageMediaGame) {
                                messageString = "\uD83C\uDFAE " + message.messageOwner.media.game.title;
                            } else if (message.messageOwner.media instanceof TLRPC.TL_messageMediaInvoice) {
                                messageString = message.messageOwner.media.title;
                            } else if (message.type == MessageObject.TYPE_MUSIC) {
                                messageString = String.format("\uD83C\uDFA7 %s - %s", message.getMusicAuthor(), message.getMusicTitle());
                            } else if (message.messageOwner.media instanceof TLRPC.TL_messageMediaStory && message.messageOwner.media.via_mention) {
                                if (message.isOut()) {
                                    long did = message.getDialogId();
                                    String username = "";
                                    TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(did);
                                    if (user != null) {
                                        username = UserObject.getFirstName(user);
                                        int index;
                                        if ((index = username.indexOf(' ')) >= 0) {
                                            username = username.substring(0, index);
                                        }
                                    }
                                    messageString = LocaleController.formatString(R.string.StoryYouMentionInDialog, username);
                                } else {
                                    messageString = getString(R.string.StoryMentionInDialog);
                                }
                            } else {
                                if (message.hasHighlightedWords() && !TextUtils.isEmpty(message.messageOwner.message)){
                                    messageString = message.messageTrimmedToHighlight;
                                    if (message.messageTrimmedToHighlight != null) {
                                        messageString = message.messageTrimmedToHighlight;
                                    }
                                    int w = getMeasuredWidth() - dp(messagePaddingStart + 23 );
                                    if (message.messageTrimmedToHighlightCut) {
                                        messageString = AndroidUtilities.ellipsizeCenterEnd(messageString, message.highlightedWords.get(0), w, currentMessagePaint, 130);
                                    }
                                } else {
                                    SpannableString stringBuilder = new SpannableString(msgText);
                                    if (message != null) {
                                        message.spoilLoginCode();
                                    }
                                    MediaDataController.addTextStyleRuns(message, stringBuilder, TextStyleSpan.FLAG_STYLE_SPOILER | TextStyleSpan.FLAG_STYLE_STRIKE);
                                    if (message != null && message.messageOwner != null) {
                                        MediaDataController.addAnimatedEmojiSpans(message.messageOwner.entities, stringBuilder, currentMessagePaint == null ? null : currentMessagePaint.getFontMetricsInt());
                                    }
                                    messageString = stringBuilder;
                                }
                                AndroidUtilities.highlightText(messageString, message.highlightedWords, resourcesProvider);
                            }
                            if (message.messageOwner.media != null && !message.isMediaEmpty()) {
                                currentMessagePaint = Theme.dialogs_messagePrintingPaint[paintIndex];
                            }
                        }
                        if (message.isReplyToStory()) {
                            SpannableStringBuilder builder = new SpannableStringBuilder(messageString);
                            builder.insert(0, "d ");
                            builder.setSpan(new ColoredImageSpan(ContextCompat.getDrawable(getContext(), R.drawable.msg_mini_replystory).mutate()), 0, 1, 0);
                            messageString = builder;
                        }
                        if (thumbsCount > 0) {
                            if (message.hasHighlightedWords() && !TextUtils.isEmpty(message.messageOwner.message)) {
                                messageString = message.messageTrimmedToHighlight;
                                if (message.messageTrimmedToHighlight != null) {
                                    messageString = message.messageTrimmedToHighlight;
                                }
                                int w = getMeasuredWidth() - dp(messagePaddingStart + 23 + (thumbSize + 2) * thumbsCount - 2 + 5);
                                if (message.messageTrimmedToHighlightCut) {
                                    messageString = AndroidUtilities.ellipsizeCenterEnd(messageString, message.highlightedWords.get(0), w, currentMessagePaint, 130).toString();
                                }
                            } else {
                                if (messageString.length() > 150) {
                                    messageString = messageString.subSequence(0, 150);
                                }
                                messageString = AndroidUtilities.replaceNewLines(messageString);
                            }
                            if (!(messageString instanceof SpannableStringBuilder)) {
                                messageString = new SpannableStringBuilder(messageString);
                            }
                            checkMessage = false;
                            SpannableStringBuilder builder = (SpannableStringBuilder) messageString;
                            builder.insert(0, " ");
                            builder.setSpan(new FixedWidthSpan(dp((thumbSize + 2) * thumbsCount - 2 + 5)), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            Emoji.replaceEmoji(builder, Theme.dialogs_messagePaint[paintIndex].getFontMetricsInt(), false);
                            if (message.hasHighlightedWords()) {
                                CharSequence s = AndroidUtilities.highlightText(builder, message.highlightedWords, resourcesProvider);
                                if (s != null) {
                                    messageString = s;
                                }
                            }
                        }
                        if (message.isForwarded() && message.needDrawForwarded()) {
                            drawForwardIcon = true;
                            SpannableStringBuilder builder = new SpannableStringBuilder(messageString);
                            builder.insert(0, "d ");
                            ColoredImageSpan coloredImageSpan = new ColoredImageSpan(ContextCompat.getDrawable(getContext(), R.drawable.mini_forwarded).mutate());
                            coloredImageSpan.setAlpha(0.9f);
                            builder.setSpan(coloredImageSpan, 0, 1, 0);
                            messageString = builder;
                        }
                    }
                }
            }
        }

        if (!drawForwardIcon && !isForumCell() && message != null && message.messageOwner != null && message.messageOwner.action instanceof TLRPC.TL_messageActionStarGift) {
            drawGiftIcon = true;
            SpannableStringBuilder builder = new SpannableStringBuilder(messageString);
            builder.insert(0, "d ");
            ColoredImageSpan coloredImageSpan = new ColoredImageSpan(ContextCompat.getDrawable(getContext(), R.drawable.mini_gift).mutate());
            coloredImageSpan.setScale(1.25f, 1.25f);
            coloredImageSpan.spaceScaleX = 0.9f;
            coloredImageSpan.setAlpha(0.9f);
            builder.setSpan(coloredImageSpan, 0, 1, 0);
            messageString = builder;
            final TLRPC.TL_messageActionStarGift action = (TLRPC.TL_messageActionStarGift) message.messageOwner.action;
            if (action.message != null && !TextUtils.isEmpty(action.message.text)) {
                currentMessagePaint = Theme.dialogs_messagePaint[paintIndex];
            }
        }

        if (lastMessageDate != 0) {
            timeString = LocaleController.stringForMessageListDate(lastMessageDate);
        } else if (message != null) {
            timeString = LocaleController.stringForMessageListDate(message.messageOwner.date);
        }

        if (titleOverride != null) {
            nameString = titleOverride;
        } else {
            if (chat != null) {
                if (useFromUserAsAvatar) {
                    if (topicIconInName == null) {
                        topicIconInName = new Drawable[1];
                    }
                    topicIconInName[0] = null;
                    nameString = MessagesController.getInstance(currentAccount).getTopicsController().getTopicIconName(chat, message, currentMessagePaint, topicIconInName);
                    if (nameString == null) {
                        nameString = "";
                    }
                } else if (chat.monoforum && chat.linked_monoforum_id != 0) {
                    final TLRPC.Chat chat2 = MessagesController.getInstance(currentAccount).getChat(chat.linked_monoforum_id);
                    if (chat2 != null) {
                        final SpannableStringBuilder sb = new SpannableStringBuilder(AndroidUtilities.escape(chat2.title));
                        sb.append(" ");
                        final int index = sb.length();
                        sb.append(getString(R.string.MonoforumSpan));
                        sb.setSpan(new FilterCreateActivity.TextSpan(getString(R.string.MonoforumSpan), 9.33f, Theme.key_windowBackgroundWhiteGrayText, resourcesProvider), index, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        nameString = sb;
                    } else {
                        nameString = AndroidUtilities.escape(chat.title);
                    }
                } else {
                    nameString = AndroidUtilities.escape(UserConfig.getChatTitleOverride(currentAccount, chat));
                }
            }
            if (chat != null) {
                if (chat.isScam()) {
                    drawScam = 1;
                    Theme.dialogs_scamDrawable.checkText();
                } else if (chat.isFake()) {
                    drawScam = 2;
                    Theme.dialogs_fakeDrawable.checkText();
                } else if (DialogObject.getEmojiStatusDocumentId(chat.emoji_status) != 0) {
                    drawPremium = true;
                    nameLayoutEllipsizeByGradient = true;
                    emojiStatus.center = LocaleController.isRTL;
                    emojiStatus.set(DialogObject.getEmojiStatusDocumentId(chat.emoji_status), false);
                    emojiStatus.setParticles(DialogObject.isEmojiStatusCollectible(chat.emoji_status), false);
                } else {
                    drawVerified = !forbidVerified && chat.isVerified();
                    drawBotVerified = !forbidVerified && chat.bot_verification_icon != 0;
                }
            }
            if (nameString != null && nameString.length() == 0) {
                nameString = getString(R.string.HiddenName);
            }
        }

        int timeWidth;
        if (drawTime) {
            final TextPaint tp = getTimeTextPaint();
            timeWidth = (int) Math.ceil(tp.measureText(timeString));
            timeLayout = new StaticLayout(timeString, tp, timeWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
            timeWidth += dp(getIsPinned() ? 24 : 0);
            if (!LocaleController.isRTL) {
                timeLeft = getMeasuredWidth() - dp(15) - timeWidth;
            } else {
                timeLeft = dp(15);
            }
        } else {
            timeWidth = 0;
            timeLayout = null;
            timeLeft = 0;
        }

        int timeLeftOffset = 0;
        if (drawLock2()) {
            if (LocaleController.isRTL) {
                lock2Left = timeLeft + timeWidth + dp(4);
            } else {
                lock2Left = timeLeft - Theme.dialogs_lock2Drawable.getIntrinsicWidth() - dp(4);
            }
            timeLeftOffset += Theme.dialogs_lock2Drawable.getIntrinsicWidth() + dp(4);
            timeWidth += timeLeftOffset;
        }

        if (!LocaleController.isRTL) {
            nameWidth = getMeasuredWidth() - nameLeft - dp(14 + 8) - timeWidth;
        } else {
            nameWidth = getMeasuredWidth() - nameLeft - dp(messagePaddingStart + 5 + 8) - timeWidth;
            nameLeft += timeWidth;
        }

        nameAdditionalsForChannelSubscriber = 0;
        if (drawPremium && emojiStatus.getDrawable() != null) {
            int w = dp(6 + 24 + 6);
            nameWidth -= w;
            nameAdditionalsForChannelSubscriber += w;
            if (LocaleController.isRTL) {
                nameLeft += w;
            }
        } else if ((dialogMuted || drawUnmute) && !drawVerified && drawScam == 0) {
            int w = dp(6) + Theme.dialogs_muteDrawable.getIntrinsicWidth();
            nameWidth -= w;
            nameAdditionalsForChannelSubscriber += w;
            if (LocaleController.isRTL) {
                nameLeft += w;
            }
        } else if (drawVerified) {
            int w = dp(6) + Theme.dialogs_verifiedDrawable.getIntrinsicWidth();
            nameWidth -= w;
            nameAdditionalsForChannelSubscriber += w;
            if (LocaleController.isRTL) {
                nameLeft += w;
            }
        } else if (drawPremium) {
            int w = dp(6 + 24 + 6);
            nameWidth -= w;
            nameAdditionalsForChannelSubscriber += w;
            if (LocaleController.isRTL) {
                nameLeft += w;
            }
        } else if (drawScam != 0) {
            int w = dp(6) + (drawScam == 1 ? Theme.dialogs_scamDrawable : Theme.dialogs_fakeDrawable).getIntrinsicWidth();
            nameWidth -= w;
            nameAdditionalsForChannelSubscriber += w;
            if (LocaleController.isRTL) {
                nameLeft += w;
            }
        }
        if (drawBotVerified) {
            nameWidth -= dp(21);
        }
        if (namePaddingEnd > 0) {
            nameWidth -= namePaddingEnd;
            if (LocaleController.isRTL) {
                nameLeft += namePaddingEnd;
            }
        }
        try {
            int ellipsizeWidth = nameWidth - dp(12);
            if (ellipsizeWidth < 0) {
                ellipsizeWidth = 0;
            }
            if (nameString instanceof String) {
                nameString = ((String) nameString).replace('\n', ' ');
            }
            CharSequence nameStringFinal = nameString;
            if (nameLayoutEllipsizeByGradient) {
                nameLayoutFits = nameStringFinal.length() == TextUtils.ellipsize(nameStringFinal, Theme.dialogs_namePaint[paintIndex], ellipsizeWidth, TextUtils.TruncateAt.END).length();
                ellipsizeWidth += dp(48);
                channelShouldUseLineWidth = nameLayoutFits;
            } else if (isForChannelSubscriberCell) {
                channelShouldUseLineWidth = nameStringFinal.length() == TextUtils.ellipsize(nameStringFinal, Theme.dialogs_namePaint[paintIndex], ellipsizeWidth, TextUtils.TruncateAt.END).length();
            }
            nameStringFinal = TextUtils.ellipsize(nameStringFinal, Theme.dialogs_namePaint[paintIndex], ellipsizeWidth, TextUtils.TruncateAt.END);
            nameStringFinal = Emoji.replaceEmoji(nameStringFinal, Theme.dialogs_namePaint[paintIndex].getFontMetricsInt(), false);
            if (message != null && message.hasHighlightedWords()) {
                CharSequence s = AndroidUtilities.highlightText(nameStringFinal, message.highlightedWords, resourcesProvider);
                if (s != null) {
                    nameStringFinal = s;
                }
            }
            nameLayout = new StaticLayout(nameStringFinal, Theme.dialogs_namePaint[paintIndex], Math.max(ellipsizeWidth, nameWidth), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
            nameLayoutTranslateX = nameLayoutEllipsizeByGradient && nameLayout.isRtlCharAt(0) ? -dp(36) : 0;
            nameLayoutEllipsizeLeft = nameLayout.isRtlCharAt(0);
        } catch (Exception e) {
            FileLog.e(e);
        }

        animatedEmojiStackName = AnimatedEmojiSpan.update(AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, this, animatedEmojiStackName, nameLayout);


        int messageWidth;
        int avatarLeft;
        int avatarTop;
        int thumbLeft;
        if (useForceThreeLines || SharedConfig.useThreeLinesLayout) {
            avatarTop = dp(11);
            messageNameTop = dp(32);
            timeTop = dp(13);
            pinTop = dp(43);
            messageWidth = getMeasuredWidth() - dp(messagePaddingStart + 21);

            if (LocaleController.isRTL) {
                buttonLeft = typingLeft = messageLeft = messageNameLeft = dp(16);
                avatarLeft = getMeasuredWidth() - dp(56 + avatarStart);
                thumbLeft = avatarLeft - dp(13 + 18);
            } else {
                buttonLeft = typingLeft = messageLeft = messageNameLeft = dp(messagePaddingStart + 6);
                avatarLeft = dp(avatarStart);
                thumbLeft = avatarLeft + dp(56 + 13);
            }
            storyParams.originalAvatarRect.set(avatarLeft, avatarTop, avatarLeft + dp(56), avatarTop + dp(56));
            for (int i = 0; i < thumbImage.length; ++i) {
                thumbImage[i].setImageCoords(thumbLeft + (thumbSize + 2) * i, avatarTop + dp(31) - (!(useForceThreeLines || SharedConfig.useThreeLinesLayout) && tags != null && !tags.isEmpty() ? dp(9) : 0), dp(18), dp(18));
            }
        } else {
            avatarTop = dp(9);
            messageNameTop = dp(31);
            timeTop = dp(16);
            pinTop = dp(39);
            messageWidth = getMeasuredWidth() - dp(messagePaddingStart + 20 - (LocaleController.isRTL ? 0 : 12));

            if (LocaleController.isRTL) {
                buttonLeft = typingLeft = messageLeft = messageNameLeft = dp(22);
                avatarLeft = getMeasuredWidth() - dp(52 + avatarStart);
                thumbLeft = avatarLeft - dp(11 + (thumbsCount * (thumbSize + 2) - 2));
            } else {
                buttonLeft = typingLeft = messageLeft = messageNameLeft = dp(messagePaddingStart + 4);
                avatarLeft = dp(avatarStart);
                thumbLeft = avatarLeft + dp(56 + 11);
            }
            storyParams.originalAvatarRect.set(avatarLeft, avatarTop, avatarLeft + dp(52), avatarTop + dp(52));
            for (int i = 0; i < thumbImage.length; ++i) {
                thumbImage[i].setImageCoords(thumbLeft + (thumbSize + 2) * i, avatarTop + dp(30) - (!(useForceThreeLines || SharedConfig.useThreeLinesLayout) && tags != null && !tags.isEmpty() ? dp(9) : 0), dp(thumbSize), dp(thumbSize));
            }
        }
        if (LocaleController.isRTL) {
            tagsRight = getMeasuredWidth() - dp(messagePaddingStart);
            tagsLeft = dp(64);
        } else {
            tagsLeft = messageLeft;
            tagsRight = getMeasuredWidth() - dp(64);
        }
        if ((!(useForceThreeLines || SharedConfig.useThreeLinesLayout) || isForumCell()) && tags != null && !tags.isEmpty()) {
            timeTop -= dp(6);
        }
        if (getIsPinned()) {
            if (!LocaleController.isRTL) {
                pinLeft = getMeasuredWidth() - Theme.dialogs_pinnedDrawable.getIntrinsicWidth() - dp(14);
            } else {
                pinLeft = dp(14);
            }
        }

        if (checkMessage) {
            if (messageString == null) {
                messageString = "";
            }
            CharSequence mess = messageString;
            if (mess.length() > 150) {
                mess = mess.subSequence(0, 150);
            }
            if (!useForceThreeLines && !SharedConfig.useThreeLinesLayout || hasTags() || messageNameString != null) {
                mess = AndroidUtilities.replaceNewLines(mess);
            } else {
                mess = AndroidUtilities.replaceTwoNewLinesToOne(mess);
            }
            messageString = Emoji.replaceEmoji(mess, Theme.dialogs_messagePaint[paintIndex].getFontMetricsInt(), false);
            if (message != null) {
                CharSequence s = AndroidUtilities.highlightText(messageString, message.highlightedWords, resourcesProvider);
                if (s != null) {
                    messageString = s;
                }
            }
        }
        messageWidth = Math.max(dp(12), messageWidth);
        buttonTop = dp(useForceThreeLines || SharedConfig.useThreeLinesLayout ? 58 : 62);
        if ((!(useForceThreeLines || SharedConfig.useThreeLinesLayout) || isForumCell()) && hasTags()) {
            buttonTop -= dp(isForumCell() ? 10 : 12);
        }
        if (isForumCell()) {
            messageTop = dp(useForceThreeLines || SharedConfig.useThreeLinesLayout ? 34 : 39);
            for (int i = 0; i < thumbImage.length; ++i) {
                thumbImage[i].setImageY(buttonTop);
            }
        } else {
            messageNameLayout = null;
            if (useForceThreeLines || SharedConfig.useThreeLinesLayout) {
                messageTop = dp(32);
                int yoff = 0;
                for (int i = 0; i < thumbImage.length; ++i) {
                    thumbImage[i].setImageY(avatarTop + yoff + dp(21));
                }
            } else {
                messageTop = dp(39);
            }
        }

        animatedEmojiStack2 = AnimatedEmojiSpan.update(AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, this, animatedEmojiStack2, messageNameLayout);


        try {
            buttonCreated = false;
            if (!TextUtils.isEmpty(buttonString)) {
                buttonString = Emoji.replaceEmoji(buttonString, currentMessagePaint.getFontMetricsInt(), false);
                CharSequence buttonStringFinal = TextUtils.ellipsize(buttonString, currentMessagePaint, messageWidth - dp(26), TextUtils.TruncateAt.END);
                buttonLayout = new StaticLayout(buttonStringFinal, currentMessagePaint, messageWidth - dp(20), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                spoilersPool2.addAll(spoilers2);
                spoilers2.clear();
                SpoilerEffect.addSpoilers(this, buttonLayout, spoilersPool2, spoilers2);
            } else {
                buttonLayout = null;
            }
        } catch (Exception e) {

        }
        animatedEmojiStack3 = AnimatedEmojiSpan.update(AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, this, animatedEmojiStack3, buttonLayout);

        try {
            if (!TextUtils.isEmpty(typingString)) {
                if ((useForceThreeLines || SharedConfig.useThreeLinesLayout) && !hasTags()) {
                    typingLayout = StaticLayoutEx.createStaticLayout(typingString, Theme.dialogs_messagePrintingPaint[paintIndex], messageWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, dp(1), false, TextUtils.TruncateAt.END, messageWidth, typingString != null ? 1 : 2);
                } else {
                    typingString = TextUtils.ellipsize(typingString, currentMessagePaint, messageWidth - dp(12), TextUtils.TruncateAt.END);
                    typingLayout = new StaticLayout(typingString, Theme.dialogs_messagePrintingPaint[paintIndex], messageWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }

        try {
            CharSequence messageStringFinal;
            // Removing links and bold spans to get rid of underlining and boldness
            if (messageString instanceof Spannable) {
                Spannable messageStringSpannable = (Spannable) messageString;
                for (Object span : messageStringSpannable.getSpans(0, messageStringSpannable.length(), Object.class)) {
                    if (span instanceof ClickableSpan || span instanceof CodeHighlighting.Span || span instanceof TypefaceSpan || span instanceof CodeHighlighting.ColorSpan || span instanceof QuoteSpan || span instanceof QuoteSpan.QuoteStyleSpan || (span instanceof StyleSpan && ((StyleSpan) span).getStyle() == android.graphics.Typeface.BOLD)) {
                        messageStringSpannable.removeSpan(span);
                    }
                }
            }
            if (!useForceThreeLines && !SharedConfig.useThreeLinesLayout || hasTags() || messageNameString != null || ChatObject.isMonoForum(chat) && ChatObject.canManageMonoForum(currentAccount, chat)) {
                if (!isForumCell() && messageString instanceof Spanned && ((Spanned) messageString).getSpans(0, messageString.length(), FixedWidthSpan.class).length <= 0) {
                    messageStringFinal = TextUtils.ellipsize(messageString, currentMessagePaint, messageWidth - dp(12 + (thumbsCount * (thumbSize + 2) - 2) + 5), TextUtils.TruncateAt.END);
                } else {
                    messageStringFinal = TextUtils.ellipsize(messageString, currentMessagePaint, messageWidth - dp(12), TextUtils.TruncateAt.END);
                }
            } else {
                messageStringFinal = messageString;
            }

            Layout.Alignment align = Layout.Alignment.ALIGN_NORMAL;
            if ((useForceThreeLines || SharedConfig.useThreeLinesLayout) && !hasTags()) {
                if (thumbsCount > 0 && messageNameString != null) {
                    messageWidth += dp(5);
                }
                messageLayout = StaticLayoutEx.createStaticLayout(messageStringFinal, currentMessagePaint, messageWidth, align, 1.0f, dp(1), false, TextUtils.TruncateAt.END, messageWidth, messageNameString != null ? 1 : 2);
            } else {
                if (thumbsCount > 0) {
                    messageWidth += dp((thumbsCount * (thumbSize + 2) - 2) + 5);
                    if (LocaleController.isRTL && !isForumCell()) {
                        messageLeft -= dp((thumbsCount * (thumbSize + 2) - 2) + 5);
                    }
                }
                messageLayout = new StaticLayout(messageStringFinal, currentMessagePaint, messageWidth, align, 1.0f, 0.0f, false);
            }
            spoilersPool.addAll(spoilers);
            spoilers.clear();
            SpoilerEffect.addSpoilers(this, messageLayout, -2, -2, spoilersPool, spoilers);
        } catch (Exception e) {
            messageLayout = null;
            FileLog.e(e);
        }
        animatedEmojiStack = AnimatedEmojiSpan.update(AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, this, animatedEmojiStack, messageLayout);

        double widthpx;
        float left;
        if (LocaleController.isRTL) {
            if (nameLayout != null && nameLayout.getLineCount() > 0) {
                left = nameLayout.getLineLeft(0);
                widthpx = Math.ceil(nameLayout.getLineWidth(0));
                nameLeft += dp(12);
                if (drawBotVerified) {
                    nameLeft += dp(21);
                }
                if (nameLayoutEllipsizeByGradient) {
                    widthpx = Math.min(nameWidth, widthpx);
                }
                if ((dialogMuted || drawUnmute) && !drawVerified && drawScam == 0) {
                    nameMuteLeft = (int) (nameLeft + (nameWidth - widthpx) - dp(6) - Theme.dialogs_muteDrawable.getIntrinsicWidth());
                } else if (drawVerified) {
                    nameMuteLeft = (int) (nameLeft + (nameWidth - widthpx) - dp(6) - Theme.dialogs_verifiedDrawable.getIntrinsicWidth());
                } else if (drawPremium) {
                    nameMuteLeft = (int) (nameLeft + (nameWidth - widthpx - left) - dp(24));
                } else if (drawScam != 0) {
                    nameMuteLeft = (int) (nameLeft + (nameWidth - widthpx) - dp(6) - (drawScam == 1 ? Theme.dialogs_scamDrawable : Theme.dialogs_fakeDrawable).getIntrinsicWidth());
                } else {
                    nameMuteLeft = (int) (nameLeft + (nameWidth - widthpx) - dp(6) - Theme.dialogs_muteDrawable.getIntrinsicWidth());
                }
                if (left == 0) {
                    if (widthpx < nameWidth) {
                        nameLeft += (nameWidth - widthpx);
                    }
                }
            }
            if (messageLayout != null) {
                int lineCount = messageLayout.getLineCount();
                if (lineCount > 0) {
                    int w = Integer.MAX_VALUE;
                    for (int a = 0; a < lineCount; a++) {
                        left = messageLayout.getLineLeft(a);
                        if (left == 0) {
                            widthpx = Math.ceil(messageLayout.getLineWidth(a));
                            w = Math.min(w, (int) (messageWidth - widthpx));
                        } else {
                            w = 0;
                            break;
                        }
                    }
                    if (w != Integer.MAX_VALUE) {
                        messageLeft += w;
                    }
                }
            }
            if (typingLayout != null) {
                int lineCount = typingLayout.getLineCount();
                if (lineCount > 0) {
                    int w = Integer.MAX_VALUE;
                    for (int a = 0; a < lineCount; a++) {
                        left = typingLayout.getLineLeft(a);
                        if (left == 0) {
                            widthpx = Math.ceil(typingLayout.getLineWidth(a));
                            w = Math.min(w, (int) (messageWidth - widthpx));
                        } else {
                            w = 0;
                            break;
                        }
                    }
                    if (w != Integer.MAX_VALUE) {
                        typingLeft += w;
                    }
                }
            }
            if (messageNameLayout != null && messageNameLayout.getLineCount() > 0) {
                left = messageNameLayout.getLineLeft(0);
                if (left == 0) {
                    widthpx = Math.ceil(messageNameLayout.getLineWidth(0));
                    if (widthpx < messageWidth) {
                        messageNameLeft += (messageWidth - widthpx);
                    }
                }
            }
            if (buttonLayout != null) {
                int lineCount = buttonLayout.getLineCount();
                if (lineCount > 0) {
                    int rightpad = Integer.MAX_VALUE;
                    for (int a = 0; a < lineCount; a++) {
                        rightpad = (int) Math.min(rightpad, buttonLayout.getWidth() - buttonLayout.getLineRight(a));
                    }
                    buttonLeft += rightpad;
                }
            }
        } else {
            if (nameLayout != null && nameLayout.getLineCount() > 0) {
                left = nameLayout.getLineRight(0);
                if (nameLayoutEllipsizeByGradient) {
                    left = Math.min(nameWidth, left);
                }
                if (left == nameWidth) {
                    widthpx = Math.ceil(nameLayout.getLineWidth(0));
                    if (nameLayoutEllipsizeByGradient) {
                        widthpx = Math.min(nameWidth, widthpx);
//                        widthpx -= dp(36);
//                        left += dp(36);
                    }
                    if (widthpx < nameWidth) {
                        nameLeft -= (nameWidth - widthpx);
                    }
                }
                if (drawBotVerified) {
                    nameLeft += dp(21);
                }
                if ((dialogMuted || true) || drawUnmute || drawVerified || drawPremium || drawScam != 0) {
                    nameMuteLeft = (int) (nameLeft + left + dp(6));
                }
            }
            if (messageLayout != null) {
                int lineCount = messageLayout.getLineCount();
                if (lineCount > 0) {
                    left = Integer.MAX_VALUE;
                    for (int a = 0; a < lineCount; a++) {
                        left = Math.min(left, messageLayout.getLineLeft(a));
                    }
                    messageLeft -= left;
                }
            }
            if (buttonLayout != null) {
                int lineCount = buttonLayout.getLineCount();
                if (lineCount > 0) {
                    left = Integer.MAX_VALUE;
                    for (int a = 0; a < lineCount; a++) {
                        left = Math.min(left, buttonLayout.getLineLeft(a));
                    }
                    buttonLeft -= left;
                }
            }
            if (typingLayout != null) {
                int lineCount = typingLayout.getLineCount();
                if (lineCount > 0) {
                    left = Integer.MAX_VALUE;
                    for (int a = 0; a < lineCount; a++) {
                        left = Math.min(left, typingLayout.getLineLeft(a));
                    }
                    typingLeft -= left;
                }
            }
            if (messageNameLayout != null && messageNameLayout.getLineCount() > 0) {
                messageNameLeft -= messageNameLayout.getLineLeft(0);
            }
        }
        if (typingLayout != null && printingStringType >= 0 && typingLayout.getText().length() > 0) {
            float x1, x2;
            if (printingStringReplaceIndex >= 0 && printingStringReplaceIndex + 1 < typingLayout.getText().length() ){
                x1 = typingLayout.getPrimaryHorizontal(printingStringReplaceIndex);
                x2 = typingLayout.getPrimaryHorizontal(printingStringReplaceIndex + 1);
            } else {
                x1 = typingLayout.getPrimaryHorizontal(0);
                x2 = typingLayout.getPrimaryHorizontal(1);
            }
        }
        updateThumbsPosition();
    }

    public void setTitleOverride(String s) {
        titleOverride = s;
    }

    private SpannableStringBuilder formatInternal(int messageFormatType, CharSequence s1, CharSequence s2) {
        SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder();
        switch (messageFormatType) {
            case 1:
                //"%2$s: \u2068%1$s\u2069"
                spannableStringBuilder.append(s2).append(": \u2068").append(s1).append("\u2069");
                break;
            case 2:
                //"\u2068%1$s\u2069"
                spannableStringBuilder.append("\u2068").append(s1).append("\u2069");
                break;
            case 3:
                //"%2$s: %1$s"
                spannableStringBuilder.append(s2).append(": ").append(s1);
                break;
            case 4:
                //"%1$s"
                spannableStringBuilder.append(s1);
                break;
        }
        return spannableStringBuilder;
    }

    private void updateThumbsPosition() {
        if (thumbsCount > 0) {
            StaticLayout layout = isForumCell() ? buttonLayout : messageLayout;
            int left = isForumCell() ? buttonLeft : messageLeft;
            if (layout == null) {
                return;
            }
            try {
                CharSequence text = layout.getText();
                if (text instanceof Spanned) {
                    FixedWidthSpan[] spans = ((Spanned) text).getSpans(0, text.length(), FixedWidthSpan.class);
                    if (spans != null && spans.length > 0) {
                        int spanOffset = ((Spanned) text).getSpanStart(spans[0]);
                        if (spanOffset < 0) {
                            spanOffset = 0;
                        }

                        float x1 = layout.getPrimaryHorizontal(spanOffset);
                        float x2 = layout.getPrimaryHorizontal(spanOffset + 1);
                        int offset = (int) Math.ceil(Math.min(x1, x2));
                        if (offset != 0 && !drawForwardIcon && !drawGiftIcon) {
                            offset += dp(3);
                        }
                        for (int i = 0; i < thumbsCount; ++i) {
                            thumbImage[i].setImageX(left + offset + dp((thumbSize + 2) * i));
                            thumbImageSeen[i] = true;
                        }
                    } else {
                        for (int i = 0; i < 3; ++i) {
                            thumbImageSeen[i] = false;
                        }
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
    }

    private CharSequence applyThumbs(CharSequence string) {
        if (thumbsCount > 0) {
            SpannableStringBuilder builder = SpannableStringBuilder.valueOf(string);
            builder.insert(0, " ");
            builder.setSpan(new FixedWidthSpan(dp((thumbSize + 2) * thumbsCount - 2 + 5)), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            return builder;
        }
        return string;
    }

    int topMessageTopicStartIndex;
    int topMessageTopicEndIndex;

    private ForumFormattedNames forumFormattedNames;

    private CharSequence formatTopicsNames() {
        if (forumFormattedNames == null) {
            forumFormattedNames = new ForumFormattedNames(this);
        }

        forumFormattedNames.formatTopicsNames(currentAccount, message, chat);
        topMessageTopicStartIndex = forumFormattedNames.topMessageTopicStartIndex;
        topMessageTopicEndIndex = forumFormattedNames.topMessageTopicEndIndex;
        lastTopicMessageUnread = forumFormattedNames.lastTopicMessageUnread;
        return forumFormattedNames.formattedNames;
    }

    public boolean isForumCell() {
        return chat != null && (chat.forum || ChatObject.isMonoForum(chat) && ChatObject.canManageMonoForum(currentAccount, chat));
    }

    public boolean isPointInsideAvatar(float x, float y) {
        if (!LocaleController.isRTL) {
            return x >= 0 && x < dp(60);
        } else {
            return x >= getMeasuredWidth() - dp(60) && x < getMeasuredWidth();
        }
    }

    public void setDialogSelected(boolean value) {
        if (isSelected != value) {
            invalidate();
        }
        isSelected = value;
    }

    public boolean checkCurrentDialogIndex(boolean frozen) {
        return false;
    }

    public void setChecked(boolean checked, boolean animated) {
        if (checkBox == null && !checked) {
            return;
        }
        if (checkBox == null) {
            checkBox = new CheckBox2(getContext(), 21, resourcesProvider) {
                @Override
                public void invalidate() {
                    super.invalidate();
                    SavedChannelCell.this.invalidate();
                }
            };
            checkBox.setColor(-1, Theme.key_windowBackgroundWhite, Theme.key_checkboxCheck);
            checkBox.setDrawUnchecked(false);
            checkBox.setDrawBackgroundAsArc(3);
            addView(checkBox);
        }
        checkBox.setChecked(checked, animated);
        checkTtl();
    }

    public boolean update(int mask) {
        return update(mask, true);
    }

    public boolean update(int mask, boolean animated) {
        boolean requestLayout = false;
        boolean rebuildLayout = false;
        boolean invalidate = false;
        boolean oldIsForumCell = isForumCell();
        drawAvatarSelector = false;
        ttlPeriod = 0;

        hasUnmutedTopics = false;
        if (isDialogCell) {
            if (mask == 0) {
                lastMessageDate = message != null ? message.messageOwner.date : 0;
                messageId = message != null ? message.getId() : 0;
            }
            drawAvatarSelector = currentDialogId != 0 && currentDialogId == RightSlidingDialogContainer.fragmentDialogId;
        } else {
            drawPin = false;
        }

        if (tags != null) {
            final boolean tagsWereEmpty = tags.isEmpty();
            if (tags.update(currentAccount, 0, currentDialogId)) {
                if (tagsWereEmpty != tags.isEmpty()) {
                    rebuildLayout = true;
                    requestLayout = true;
                }
                invalidate = true;
            }
        }

        if (mask != 0) {
            boolean continueUpdate = false;
            if ((mask & MessagesController.UPDATE_MASK_EMOJI_STATUS) != 0) {
                long dialogBotVerificationIcon = 0;
                if (chat != null) {
                    chat = MessagesController.getInstance(currentAccount).getChat(chat.id);
                    if (chat != null && DialogObject.getEmojiStatusDocumentId(chat.emoji_status) != 0)  {
                        nameLayoutEllipsizeByGradient = true;
                        emojiStatus.set(DialogObject.getEmojiStatusDocumentId(chat.emoji_status), animated);
                        emojiStatus.setParticles(DialogObject.isEmojiStatusCollectible(chat.emoji_status), animated);
                    } else {
                        nameLayoutEllipsizeByGradient = true;
                        emojiStatus.set(PremiumGradient.getInstance().premiumStarDrawableMini, animated);
                        emojiStatus.setParticles(false, animated);
                    }
                    dialogBotVerificationIcon = DialogObject.getBotVerificationIcon(chat);
                    invalidate = true;
                }
                if (dialogBotVerificationIcon != 0) {
                    if (!drawBotVerified) {
                        continueUpdate = true;
                    }
                    botVerification.set(dialogBotVerificationIcon, animated);
                } else {
                    if (drawBotVerified) {
                        continueUpdate = true;
                    }
                    botVerification.set((Drawable) null, animated);
                }
            }
            if (isDialogCell) {
                if ((mask & MessagesController.UPDATE_MASK_USER_PRINT) != 0) {
                    CharSequence printString = MessagesController.getInstance(currentAccount).getPrintingString(currentDialogId, getTopicId(), true);
                    if (lastPrintString != null && printString == null || lastPrintString == null && printString != null || lastPrintString != null && !lastPrintString.equals(printString)) {
                        continueUpdate = true;
                    }
                }
            }
            if (!continueUpdate && (mask & MessagesController.UPDATE_MASK_MESSAGE_TEXT) != 0) {
                if (message != null && message.messageText != lastMessageString) {
                    continueUpdate = true;
                }
            }
            if (!continueUpdate && (mask & MessagesController.UPDATE_MASK_AVATAR) != 0) {
                if (chat == null || chat.monoforum && ChatObject.canManageMonoForum(currentAccount, chat)) {
                    if (forumFormattedNames != null) {
                        forumFormattedNames.invalidateSpans();
                    }
                    continueUpdate = true;
                }
            }
            if (!continueUpdate && (mask & MessagesController.UPDATE_MASK_NAME) != 0) {
                if (chat == null) {
                    continueUpdate = true;
                }
            }
            if (!continueUpdate && (mask & MessagesController.UPDATE_MASK_CHAT_AVATAR) != 0) {
                continueUpdate = true;
            }
            if (!continueUpdate && (mask & MessagesController.UPDATE_MASK_CHAT_NAME) != 0) {
                continueUpdate = true;
            }
            if (!continueUpdate) {
                if (message != null && lastUnreadState != message.isUnread()) {
                    lastUnreadState = message.isUnread();
                    continueUpdate = true;
                }
            }
            if (!continueUpdate && (mask & MessagesController.UPDATE_MASK_SEND_STATE) != 0) {
                if (message != null && lastSendState != message.messageOwner.send_state) {
                    lastSendState = message.messageOwner.send_state;
                    continueUpdate = true;
                }
            }

            if (!continueUpdate) {
                //if (invalidate) {
                    invalidate();
               // }
                return requestLayout;
            }
        }

        chat = null;

        long dialogId;
        drawUnmute = false;
        if (forumTopic != null) {
            boolean allDialogMuted = MessagesController.getInstance(currentAccount).isDialogMuted(currentDialogId, 0);
            topicMuted = MessagesController.getInstance(currentAccount).isDialogMuted(currentDialogId, forumTopic.id);
            if (allDialogMuted == topicMuted) {
                dialogMuted = false;
                drawUnmute = false;
            } else {
                dialogMuted = topicMuted;
                drawUnmute = !topicMuted;
            }
        } else {
            dialogMuted = isDialogCell && MessagesController.getInstance(currentAccount).isDialogMuted(currentDialogId, getTopicId());
        }

        dialogId = currentDialogId;

        if (dialogId != 0) {
            chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
            if (!isDialogCell && chat != null && chat.migrated_to != null) {
                TLRPC.Chat chat2 = MessagesController.getInstance(currentAccount).getChat(chat.migrated_to.channel_id);
                if (chat2 != null) {
                    chat = chat2;
                }
            }
        }

        if (useFromUserAsAvatar && message != null) {
            avatarDrawable.setInfo(currentAccount, message.getFromPeerObject());
            avatarImage.setForUserOrChat(message.getFromPeerObject(), avatarDrawable, currentAccount);
        } else if (chat != null) {
            TLRPC.Chat chat = this.chat;
            if (chat.monoforum) {
                ForumUtilities.setMonoForumAvatar(currentAccount, chat, avatarDrawable, avatarImage);
                avatarDrawable.setScaleSize(1f);
            } else {
                avatarDrawable.setInfo(currentAccount, chat);
                avatarImage.setForUserOrChat(chat, avatarDrawable);
            }
        }

        drawMonoforumAvatar = chat != null && chat.monoforum;
        avatarImage.setRoundRadius(drawMonoforumAvatar ? 1 : chat != null && chat.forum && !useFromUserAsAvatar ? dp(16) : dp(28));

        if (getMeasuredWidth() != 0 || getMeasuredHeight() != 0) {
            rebuildLayout = true;
        }
        if (!invalidate) {
            boolean currentStoriesIsEmpty = storyParams.currentState == StoriesUtilities.STATE_EMPTY;
            boolean newStateStoriesIsEmpty = StoriesUtilities.getPredictiveUnreadState(MessagesController.getInstance(currentAccount).getStoriesController(), getDialogId()) == StoriesUtilities.STATE_EMPTY;
            if (!newStateStoriesIsEmpty || (!currentStoriesIsEmpty && newStateStoriesIsEmpty)) {
                invalidate = true;
            }
        }

        if (!animated) {
            dialogMutedProgress = (dialogMuted || drawUnmute) ? 1f : 0f;
        }
       // if (invalidate) {
            invalidate();
       // }
        if (isForumCell() != oldIsForumCell) {
            requestLayout = true;
        }
        if (rebuildLayout) {
            if (attachedToWindow) {
                buildLayout();
            } else {
                updateLayout = true;
            }
        }
        return requestLayout;
    }

    private int getTopicId() {
        return forumTopic == null ? 0 : forumTopic.id;
    }

    @SuppressLint("DrawAllocation")
    @Override
    protected void onDraw(Canvas canvas) {
        if (currentDialogId == 0) {
            return;
        }
        if (!visibleOnScreen) {
            return;
        }

        boolean needInvalidate = false;

        float gtx = 0, gty = 0;
        boolean emojiStatusVisible = false;

        if (clipProgress != 0.0f && Build.VERSION.SDK_INT != 24) {
            canvas.save();
            canvas.clipRect(0, topClip * clipProgress, getMeasuredWidth(), getMeasuredHeight() - (int) (bottomClip * clipProgress));
        }

        float cornersRadius = dp(8);
        if (isSelected) {
            rect.set(0, 0, getMeasuredWidth(), AndroidUtilities.lerp(getMeasuredHeight(), getCollapsedHeight(), 0));
            rect.offset(0, -translateY + collapseOffset);
            canvas.drawRoundRect(rect, cornersRadius, cornersRadius, Theme.dialogs_tabletSeletedPaint);
        }

        canvas.save();
        canvas.translate(0, 0);
        if (getIsPinned() || drawPinBackground) {
            Theme.dialogs_pinnedPaint.setColor(Theme.getColor(Theme.key_chats_pinnedOverlay, resourcesProvider));
            Theme.dialogs_pinnedPaint.setAlpha((int) (Theme.dialogs_pinnedPaint.getAlpha()));
            //canvas.drawRect(-xOffset, 0, getMeasuredWidth(), getMeasuredHeight() - translateY, Theme.dialogs_pinnedPaint);
        }
        canvas.restore();

        updateHelper.updateAnimationValues();

        if (collapseOffset != 0) {
            canvas.save();
            canvas.translate(0, collapseOffset);
            gty += collapseOffset;
        }

        int restoreToCount = -1;

        int nameTop = dp(useForceThreeLines || SharedConfig.useThreeLinesLayout ? 10 : 14);
        if ((!(useForceThreeLines || SharedConfig.useThreeLinesLayout) || isForumCell()) && hasTags()) {
            nameTop -= dp(isForumCell() ? 8 : 9);
        }
        if (nameLayout != null) {
            if (nameLayoutEllipsizeByGradient && !nameLayoutFits) {
                if (nameLayoutEllipsizeLeft && fadePaint == null) {
                    fadePaint = new Paint();
                    fadePaint.setShader(new LinearGradient(0, 0, dp(24), 0, new int[]{0xffffffff, 0}, new float[]{0f, 1f}, Shader.TileMode.CLAMP));
                    fadePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
                } else if (fadePaintBack == null) {
                    fadePaintBack = new Paint();
                    fadePaintBack.setShader(new LinearGradient(0, 0, dp(24), 0, new int[]{0, 0xffffffff}, new float[]{0f, 1f}, Shader.TileMode.CLAMP));
                    fadePaintBack.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
                }
                canvas.saveLayerAlpha(0, 0, getMeasuredWidth(), getMeasuredHeight(), 255, Canvas.ALL_SAVE_FLAG);
                canvas.clipRect(nameLeft, 0, nameLeft + nameWidth, getMeasuredHeight());
            }

            Theme.dialogs_namePaint[paintIndex].setColor(Theme.dialogs_namePaint[paintIndex].linkColor = Theme.getColor(Theme.key_chats_name, resourcesProvider));
            canvas.save();
            canvas.translate(nameLeft + nameLayoutTranslateX, nameTop);
            SpoilerEffect.layoutDrawMaybe(nameLayout, canvas);
            AnimatedEmojiSpan.drawAnimatedEmojis(canvas, nameLayout, animatedEmojiStackName, -.075f, null, 0, 0, 0, 1f, getAdaptiveEmojiColorFilter(0, nameLayout.getPaint().getColor()));
            canvas.restore();
            if (nameLayoutEllipsizeByGradient && !nameLayoutFits) {
                canvas.save();
                if (nameLayoutEllipsizeLeft) {
                    canvas.translate(nameLeft, 0);
                    canvas.drawRect(0, 0, dp(24), getMeasuredHeight(), fadePaint);
                } else {
                    canvas.translate(nameLeft + nameWidth - dp(24), 0);
                    canvas.drawRect(0, 0, dp(24), getMeasuredHeight(), fadePaintBack);
                }
                canvas.restore();
                canvas.restore();
            }
        }

        if (timeLayout != null) {
            canvas.save();
            canvas.translate(timeLeft, timeTop);

            final TextPaint timeTextPaint = getTimeTextPaint();
            if (getIsPinned()) {
                canvas.translate(dp(20), 0);

                final float y = timeLayout.getHeight() / 2f - dp(17 / 2f);
                final float l = -dp(20);
                final float r = timeLayout.getWidth() + dp(6);

                final Drawable pd = Theme.dialogs_pinnedDrawable2;

                final int dy = (int) (y + (dp(17) - pd.getIntrinsicHeight()) / 2f);
                final int dx = (int) l + dp(4);

                pd.setBounds(dx, dy,
                    dx + pd.getIntrinsicWidth(),
                    dy + pd.getIntrinsicHeight());


                int a = timeTextPaint.getAlpha();
                timeTextPaint.setAlpha(27);
                canvas.drawRoundRect(l, y, r, y + dp(17), dp(17 / 2f), dp(17 / 2f), timeTextPaint);
                timeTextPaint.setAlpha(a);

                pd.draw(canvas);
            }

            final int tpColor = timeLayout.getPaint().getColor();
            final boolean updateTextColor = tpColor != timeTextPaint.getColor();
            if (updateTextColor) {
                timeLayout.getPaint().setColor(timeTextPaint.getColor());
            }
            SpoilerEffect.layoutDrawMaybe(timeLayout, canvas);
            if (updateTextColor) {
                timeLayout.getPaint().setColor(tpColor);
            }
            canvas.restore();
        }

        if (drawLock2()) {
            Theme.dialogs_lock2Drawable.setBounds(
                    lock2Left,
                    timeTop + (timeLayout.getHeight() - Theme.dialogs_lock2Drawable.getIntrinsicHeight()) / 2,
                    lock2Left + Theme.dialogs_lock2Drawable.getIntrinsicWidth(),
                    timeTop + (timeLayout.getHeight() - Theme.dialogs_lock2Drawable.getIntrinsicHeight()) / 2 + Theme.dialogs_lock2Drawable.getIntrinsicHeight()
            );
            Theme.dialogs_lock2Drawable.draw(canvas);
        }

        if (messageNameLayout != null && !isForumCell()) {
            Theme.dialogs_messageNamePaint.setColor(Theme.dialogs_messageNamePaint.linkColor = Theme.getColor(Theme.key_chats_nameMessage_threeLines, resourcesProvider));
            canvas.save();
            canvas.translate(messageNameLeft, messageNameTop);
            try {
                SpoilerEffect.layoutDrawMaybe(messageNameLayout, canvas);
                AnimatedEmojiSpan.drawAnimatedEmojis(canvas, messageNameLayout, animatedEmojiStack2, -.075f, null, 0, 0, 0, 1f, getAdaptiveEmojiColorFilter(1, messageNameLayout.getPaint().getColor()));
            } catch (Exception e) {
                FileLog.e(e);
            }
            canvas.restore();
        }

        if (messageLayout != null) {
            Theme.dialogs_messagePaint[paintIndex].setColor(Theme.dialogs_messagePaint[paintIndex].linkColor = Theme.getColor(Theme.key_chats_message, resourcesProvider));
            float top;
            float typingAnimationOffset = dp(14);
            if (updateHelper.typingOutToTop) {
                top = messageTop - typingAnimationOffset * updateHelper.typingProgres;
            } else {
                top = messageTop + typingAnimationOffset * updateHelper.typingProgres;
            }
            if ((!(useForceThreeLines || SharedConfig.useThreeLinesLayout) || isForumCell()) && hasTags()) {
                top -= dp(isForumCell() ? 10 : 11);
            }
            if (updateHelper.typingProgres != 1f) {
                canvas.save();
                canvas.translate(messageLeft, top);
                int oldAlpha = messageLayout.getPaint().getAlpha();
                messageLayout.getPaint().setAlpha((int) (oldAlpha * (1f - updateHelper.typingProgres)));
                if (!spoilers.isEmpty()) {
                    try {
                        canvas.save();
                        SpoilerEffect.clipOutCanvas(canvas, spoilers);
                        SpoilerEffect.layoutDrawMaybe(messageLayout, canvas);
                        AnimatedEmojiSpan.drawAnimatedEmojis(canvas, messageLayout, animatedEmojiStack, -.075f, spoilers, 0, 0, 0, 1f, getAdaptiveEmojiColorFilter(2, messageLayout.getPaint().getColor()));
                        canvas.restore();

                        for (int i = 0; i < spoilers.size(); i++) {
                            SpoilerEffect eff = spoilers.get(i);
                            eff.setColor(messageLayout.getPaint().getColor());
                            eff.draw(canvas);
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                } else {
                    SpoilerEffect.layoutDrawMaybe(messageLayout, canvas);
                    AnimatedEmojiSpan.drawAnimatedEmojis(canvas, messageLayout, animatedEmojiStack, -.075f, null, 0, 0, 0, 1f, getAdaptiveEmojiColorFilter(2, messageLayout.getPaint().getColor()));
                }
                messageLayout.getPaint().setAlpha(oldAlpha);
                canvas.restore();
            }

            canvas.save();
            if (updateHelper.typingOutToTop) {
                top = messageTop + typingAnimationOffset * (1f - updateHelper.typingProgres);
            } else {
                top = messageTop - typingAnimationOffset * (1f - updateHelper.typingProgres);
            }
            if ((!(useForceThreeLines || SharedConfig.useThreeLinesLayout) || isForumCell()) && hasTags()) {
                top -= dp(isForumCell() ? 10 : 11);
            }
            canvas.translate(typingLeft, top);
            if (typingLayout != null && updateHelper.typingProgres > 0) {
                int oldAlpha = typingLayout.getPaint().getAlpha();
                typingLayout.getPaint().setAlpha((int) (oldAlpha * updateHelper.typingProgres));
                typingLayout.draw(canvas);
                typingLayout.getPaint().setAlpha(oldAlpha);
            }
            canvas.restore();

            if (typingLayout != null && (printingStringType >= 0 || (updateHelper.typingProgres > 0 && updateHelper.lastKnownTypingType >= 0))) {
                int type = printingStringType >= 0 ? printingStringType : updateHelper.lastKnownTypingType;
                StatusDrawable statusDrawable = Theme.getChatStatusDrawable(type);
                if (statusDrawable != null) {
                    canvas.save();
                    int color = Theme.getColor(Theme.key_chats_actionMessage);
                    statusDrawable.setColor(ColorUtils.setAlphaComponent(color, (int) (Color.alpha(color) * updateHelper.typingProgres)));
                    if (updateHelper.typingOutToTop) {
                        top = messageTop + typingAnimationOffset * (1f - updateHelper.typingProgres);
                    } else {
                        top = messageTop - typingAnimationOffset * (1f - updateHelper.typingProgres);
                    }
                    if ((!(useForceThreeLines || SharedConfig.useThreeLinesLayout) || isForumCell()) && hasTags()) {
                        top -= dp(isForumCell() ? 10 : 11);
                    }
                    statusDrawable.draw(canvas);
                    invalidate();
                    canvas.restore();
                }
            }
        }

        if (buttonLayout != null) {
            canvas.save();
            if (buttonBackgroundPaint == null) {
                buttonBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            }
            if (canvasButton == null) {
                canvasButton = new CanvasButton(this);
                canvasButton.setDelegate(() -> {
                    if (delegate != null) {
                        delegate.onButtonClicked(this);
                    }
                });
                canvasButton.setLongPress(() -> {
                    if (delegate != null) {
                        delegate.onButtonLongPress(this);
                    }
                });
            }

            if (lastTopicMessageUnread && topMessageTopicEndIndex != topMessageTopicStartIndex) {
                canvasButton.setColor(ColorUtils.setAlphaComponent(currentMessagePaint.getColor(), Theme.isCurrentThemeDark() ? 36 : 26));
                if (!buttonCreated) {
                    canvasButton.rewind();
                    if (topMessageTopicEndIndex != topMessageTopicStartIndex && topMessageTopicEndIndex > 0) {
                        float top = messageTop;
                        if ((!(useForceThreeLines || SharedConfig.useThreeLinesLayout) || isForumCell()) && hasTags()) {
                            top -= dp(isForumCell() ? 10 : 11);
                        }
                        AndroidUtilities.rectTmp.set(messageLeft + dp(2) + messageLayout.getPrimaryHorizontal(0), top, messageLeft + messageLayout.getPrimaryHorizontal(Math.min(messageLayout.getText().length(), topMessageTopicEndIndex)) - dp(3), buttonTop - dp(4));
                        AndroidUtilities.rectTmp.inset(-dp(8), -dp(4));
                        if (AndroidUtilities.rectTmp.right > AndroidUtilities.rectTmp.left) {
                            canvasButton.addRect(AndroidUtilities.rectTmp);
                        }
                    }

                    float buttonLayoutLeft = buttonLayout.getLineLeft(0);
                    AndroidUtilities.rectTmp.set(buttonLeft + buttonLayoutLeft + dp(2), buttonTop + dp(2), buttonLeft + buttonLayoutLeft + buttonLayout.getLineWidth(0) + dp(12), buttonTop + buttonLayout.getHeight());
                    AndroidUtilities.rectTmp.inset(-dp(8), -dp(3));
                    canvasButton.addRect(AndroidUtilities.rectTmp);
                }
                canvasButton.draw(canvas);

                Theme.dialogs_forum_arrowDrawable.setAlpha(125);
                setDrawableBounds(Theme.dialogs_forum_arrowDrawable, AndroidUtilities.rectTmp.right - dp(18), AndroidUtilities.rectTmp.top + (AndroidUtilities.rectTmp.height() - Theme.dialogs_forum_arrowDrawable.getIntrinsicHeight()) / 2f);
                Theme.dialogs_forum_arrowDrawable.draw(canvas);
            }


            canvas.translate(buttonLeft, buttonTop);
            if (!spoilers2.isEmpty()) {
                try {
                    canvas.save();
                    SpoilerEffect.clipOutCanvas(canvas, spoilers2);
                    SpoilerEffect.layoutDrawMaybe(buttonLayout, canvas);
                    AnimatedEmojiSpan.drawAnimatedEmojis(canvas, buttonLayout, animatedEmojiStack3, -.075f, spoilers2, 0, 0, 0, 1f, getAdaptiveEmojiColorFilter(3, buttonLayout.getPaint().getColor()));
                    canvas.restore();

                    for (int i = 0; i < spoilers2.size(); i++) {
                        SpoilerEffect eff = spoilers2.get(i);
                        eff.setColor(buttonLayout.getPaint().getColor());
                        eff.draw(canvas);
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            } else {
                SpoilerEffect.layoutDrawMaybe(buttonLayout, canvas);
                AnimatedEmojiSpan.drawAnimatedEmojis(canvas, buttonLayout, animatedEmojiStack3, -.075f, null, 0, 0, 0, 1f, getAdaptiveEmojiColorFilter(3, buttonLayout.getPaint().getColor()));
            }
            canvas.restore();
        }

        if (drawBotVerified) {
            int y = dp(useForceThreeLines || SharedConfig.useThreeLinesLayout ? 12.5f : 15.5f);
            if ((!(useForceThreeLines || SharedConfig.useThreeLinesLayout) || isForumCell()) && hasTags()) {
                y -= dp(9);
            }
            if (botVerification != null) {
                botVerification.setBounds(
                    nameLeft - dp(17 + 2),
                    y + dp(-1),
                    nameLeft - dp(2),
                    y + dp(17 - 1)
                );
                botVerification.setColor(Theme.getColor(Theme.key_chats_verifiedBackground, resourcesProvider));
                botVerification.draw(canvas);
            }
        }
        boolean drawMuted = drawUnmute || dialogMuted;
        if ((drawMuted || dialogMutedProgress > 0) && !drawVerified && drawScam == 0 && !drawPremium) {
            if (drawMuted && dialogMutedProgress != 1f) {
                dialogMutedProgress += 16 / 150f;
                if (dialogMutedProgress > 1f) {
                    dialogMutedProgress = 1f;
                } else {
                    invalidate();
                }
            } else if (!drawMuted && dialogMutedProgress != 0f) {
                dialogMutedProgress -= 16 / 150f;
                if (dialogMutedProgress < 0f) {
                    dialogMutedProgress = 0f;
                } else {
                    invalidate();
                }
            }
            float muteX = nameMuteLeft - dp(useForceThreeLines || SharedConfig.useThreeLinesLayout ? 0 : 1);
            float muteY = dp(SharedConfig.useThreeLinesLayout ? 13.5f : 17.5f);
            if ((!(useForceThreeLines || SharedConfig.useThreeLinesLayout) || isForumCell()) && hasTags()) {
                muteY -= dp(isForumCell() ? 8 : 9);
            }
            setDrawableBounds(Theme.dialogs_muteDrawable, muteX, muteY);
            setDrawableBounds(Theme.dialogs_unmuteDrawable, muteX, muteY);
            if (dialogMutedProgress != 1f) {
                canvas.save();
                canvas.scale(dialogMutedProgress, dialogMutedProgress, Theme.dialogs_muteDrawable.getBounds().centerX(), Theme.dialogs_muteDrawable.getBounds().centerY());
                if (drawUnmute) {
                    Theme.dialogs_unmuteDrawable.setAlpha((int) (255 * dialogMutedProgress));
                    Theme.dialogs_unmuteDrawable.draw(canvas);
                    Theme.dialogs_unmuteDrawable.setAlpha(255);
                } else {
                    Theme.dialogs_muteDrawable.setAlpha((int) (255 * dialogMutedProgress));
                    Theme.dialogs_muteDrawable.draw(canvas);
                    Theme.dialogs_muteDrawable.setAlpha(255);
                }
                canvas.restore();
            } else {
                if (drawUnmute) {
                    Theme.dialogs_unmuteDrawable.draw(canvas);
                } else {
                    Theme.dialogs_muteDrawable.draw(canvas);
                }
            }

        } else if (drawVerified) {
            float y = dp(useForceThreeLines || SharedConfig.useThreeLinesLayout ? 13.5f : 16.5f);
            if ((!(useForceThreeLines || SharedConfig.useThreeLinesLayout) || isForumCell()) && hasTags()) {
                y -= dp(9);
            }
            setDrawableBounds(Theme.dialogs_verifiedDrawable, nameMuteLeft - dp(1), y);
            setDrawableBounds(Theme.dialogs_verifiedCheckDrawable, nameMuteLeft - dp(1), y);
            Theme.dialogs_verifiedDrawable.draw(canvas);
            Theme.dialogs_verifiedCheckDrawable.draw(canvas);
        } else if (drawPremium) {
            int y = dp(useForceThreeLines || SharedConfig.useThreeLinesLayout ? 12.5f : 15.5f);
            if ((!(useForceThreeLines || SharedConfig.useThreeLinesLayout) || isForumCell()) && hasTags()) {
                y -= dp(9);
            }
            if (emojiStatus != null) {
                emojiStatusView.setTranslationX(gtx + nameMuteLeft - dp(2));
                emojiStatusView.setTranslationY(gty + y - dp(4));
                emojiStatusVisible = true;
                emojiStatus.setColor(Theme.getColor(Theme.key_chats_verifiedBackground, resourcesProvider));
            } else {
                Drawable premiumDrawable = PremiumGradient.getInstance().premiumStarDrawableMini;
                setDrawableBounds(premiumDrawable, nameMuteLeft - dp(1), dp(useForceThreeLines || SharedConfig.useThreeLinesLayout ? 12.5f : 15.5f));
                premiumDrawable.draw(canvas);
            }
        } else if (drawScam != 0) {
            int y = dp(useForceThreeLines || SharedConfig.useThreeLinesLayout ? 12 : 15);
            if ((!(useForceThreeLines || SharedConfig.useThreeLinesLayout) || isForumCell()) && hasTags()) {
                y -= dp(9);
            }
            setDrawableBounds((drawScam == 1 ? Theme.dialogs_scamDrawable : Theme.dialogs_fakeDrawable), nameMuteLeft, y);
            (drawScam == 1 ? Theme.dialogs_scamDrawable : Theme.dialogs_fakeDrawable).draw(canvas);
        }

        if (drawReorder || reorderIconProgress != 0) {
            if (!LocaleController.isRTL) {
                final Paint reorderGradientPaint = getPaintReorderGradient();
                reorderGradientPaint.setAlpha((int) (reorderIconProgress * 255));
                canvas.save();
                canvas.translate(pinLeft - dp(24), pinTop);
                canvas.drawRect(0, 0, getMeasuredWidth() - (pinLeft - dp(24)), dp(24), reorderGradientPaint);
                canvas.restore();
            }

            Theme.dialogs_reorderDrawable.setAlpha((int) (reorderIconProgress * 255));
            setDrawableBounds(Theme.dialogs_reorderDrawable, pinLeft, pinTop);
            Theme.dialogs_reorderDrawable.draw(canvas);
        }

        if (thumbsCount > 0 && updateHelper.typingProgres != 1f) {
            float alpha = 1f;
            if (updateHelper.typingProgres > 0) {
                alpha = (1f - updateHelper.typingProgres);
                canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), (int) (255 * alpha), Canvas.ALL_SAVE_FLAG);
                float top;
                if (updateHelper.typingOutToTop) {
                    top = -dp(14) * updateHelper.typingProgres;
                } else {
                    top = dp(14) * updateHelper.typingProgres;
                }
                canvas.translate(0, top);
            }
            for (int i = 0; i < thumbsCount; ++i) {
                if (!thumbImageSeen[i]) {
                    continue;
                }
                if (thumbBackgroundPaint == null) {
                    thumbBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                    thumbBackgroundPaint.setShadowLayer(dp(1.34f), 0, dp(0.34f), 0x18000000);
                    thumbBackgroundPaint.setColor(0x00000000);
                }
                AndroidUtilities.rectTmp.set(
                        thumbImage[i].getImageX(),
                        thumbImage[i].getImageY(),
                        thumbImage[i].getImageX2(),
                        thumbImage[i].getImageY2()
                );
                //canvas.drawRoundRect(
                //        AndroidUtilities.rectTmp,
                //        thumbImage[i].getRoundRadius()[0],
                //        thumbImage[i].getRoundRadius()[1],
                //        thumbBackgroundPaint
                //);
                thumbImage[i].draw(canvas);
                if (drawSpoiler[i]) {
                    if (thumbPath == null) {
                        thumbPath = new Path();
                    } else {
                        thumbPath.rewind();
                    }
                    thumbPath.addRoundRect(AndroidUtilities.rectTmp, thumbImage[i].getRoundRadius()[0], thumbImage[i].getRoundRadius()[1], Path.Direction.CW);

                    canvas.save();
                    canvas.clipPath(thumbPath);

                    int sColor = Color.WHITE;
                    if (thumbSpoiler == null) {
                        thumbSpoiler = new SpoilerEffect();
                    }
                    thumbSpoiler.setColor(ColorUtils.setAlphaComponent(sColor, (int) (Color.alpha(sColor) * 0.325f)));
                    thumbSpoiler.setBounds((int) thumbImage[i].getImageX(), (int) thumbImage[i].getImageY(), (int) thumbImage[i].getImageX2(), (int) thumbImage[i].getImageY2());
                    thumbSpoiler.draw(canvas);
                    invalidate();

                    canvas.restore();
                }
                if (drawPlay[i]) {
                    int x = (int) (thumbImage[i].getCenterX() - Theme.dialogs_playDrawable.getIntrinsicWidth() / 2);
                    int y = (int) (thumbImage[i].getCenterY() - Theme.dialogs_playDrawable.getIntrinsicHeight() / 2);
                    setDrawableBounds(Theme.dialogs_playDrawable, x, y);
                    Theme.dialogs_playDrawable.draw(canvas);
                }
            }

            if (updateHelper.typingProgres > 0) {
                canvas.restore();
            }
        }

        if (tags != null && !tags.isEmpty()) {
            canvas.save();
            canvas.translate(tagsLeft, getMeasuredHeight() - dp(21.66f) - (useSeparator ? 1 : 0));
            tags.draw(canvas, tagsRight - tagsLeft);
            canvas.restore();
        }

        if (restoreToCount != -1) {
            canvas.restoreToCount(restoreToCount);
        }


        if (drawAvatar) {
            if (drawMonoforumAvatar) {
                if (bubbleClip == null) {
                    bubbleClip = new PhotoBubbleClip();
                }
                bubbleClip.setBounds((int) storyParams.originalAvatarRect.centerX(), (int) storyParams.originalAvatarRect.centerY(), (int) (storyParams.originalAvatarRect.width() / 2));
                canvas.save();
                canvas.clipPath(bubbleClip);
                avatarImage.setImageCoords(storyParams.originalAvatarRect);
                avatarImage.draw(canvas);
                canvas.restore();
            } else {
                storyParams.drawHiddenStoriesAsSegments = false;
                int s = storyParams.forceState;
                StoriesUtilities.drawAvatarWithStory(currentDialogId, canvas, avatarImage, storyParams);
                if (storyParams.drawnLive) {
                    checkTtl();
                }
                storyParams.forceState = s;
            }
        }

        if (avatarImage.getVisible()) {
            if (drawAvatarOverlays(canvas)) {
                needInvalidate = true;
            }
        }

        if (collapseOffset != 0) {
            canvas.restore();
        }

        if (useSeparator) {
            int left;
            left = dp(messagePaddingStart);

            float y = getMeasuredHeight() - 1;
            if (LocaleController.isRTL) {
                canvas.drawLine(0, y, getMeasuredWidth() - left, y, Theme.dividerPaint);
            } else {
                canvas.drawLine(left, y, getMeasuredWidth(), y, Theme.dividerPaint);
            }
        }

        if (clipProgress != 0.0f) {
            if (Build.VERSION.SDK_INT != 24) {
                canvas.restore();
            } else {
                Theme.dialogs_pinnedPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
                canvas.drawRect(0, 0, getMeasuredWidth(), topClip * clipProgress, Theme.dialogs_pinnedPaint);
                canvas.drawRect(0, getMeasuredHeight() - (int) (bottomClip * clipProgress), getMeasuredWidth(), getMeasuredHeight(), Theme.dialogs_pinnedPaint);
            }
        }

        if (drawReorder || reorderIconProgress != 0.0f) {
            if (drawReorder) {
                if (reorderIconProgress < 1.0f) {
                    reorderIconProgress += 16f / 170.0f;
                    if (reorderIconProgress > 1.0f) {
                        reorderIconProgress = 1.0f;
                    }
                    needInvalidate = true;
                }
            } else {
                if (reorderIconProgress > 0.0f) {
                    reorderIconProgress -= 16f / 170.0f;
                    if (reorderIconProgress < 0.0f) {
                        reorderIconProgress = 0.0f;
                    }
                    needInvalidate = true;
                }
            }
        }

        if (drawRevealBackground) {
            if (currentRevealBounceProgress < 1.0f) {
                currentRevealBounceProgress += 16f / 170.0f;
                if (currentRevealBounceProgress > 1.0f) {
                    currentRevealBounceProgress = 1.0f;
                    needInvalidate = true;
                }
            }
            if (currentRevealProgress < 1.0f) {
                currentRevealProgress += 16f / 300.0f;
                if (currentRevealProgress > 1.0f) {
                    currentRevealProgress = 1.0f;
                }
                needInvalidate = true;
            }
        } else {
            if (currentRevealBounceProgress == 1.0f) {
                currentRevealBounceProgress = 0.0f;
                needInvalidate = true;
            }
            if (currentRevealProgress > 0.0f) {
                currentRevealProgress -= 16f / 300.0f;
                if (currentRevealProgress < 0.0f) {
                    currentRevealProgress = 0.0f;
                }
                needInvalidate = true;
            }
        }

        emojiStatusView.setVisibility(emojiStatusVisible ? View.VISIBLE : View.INVISIBLE);

        if (needInvalidate) {
            invalidate();
        }
    }

    private TextPaint getTimeTextPaint() {
        return Theme.dialogs_timePaint;
    }

    private boolean isCounterMuted() {
        return chat != null && chat.forum && forumTopic == null ? !hasUnmutedTopics : dialogMuted;
    }


    private int reorderGradientLastColor;
    private Paint reorderGradientPaint;
    private Paint getPaintReorderGradient() {
        final int color = Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider);
        if (reorderGradientLastColor != color || reorderGradientPaint == null) {
            reorderGradientLastColor = color;

            if (reorderGradientPaint == null) {
                reorderGradientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            }

            LinearGradient shader = new LinearGradient(0, 0, dp(24), 0, new int[]{0, color}, new float[] {0, 1}, Shader.TileMode.CLAMP);
            reorderGradientPaint.setShader(shader);
        }
        return reorderGradientPaint;
    }

    private PremiumGradient.PremiumGradientTools premiumGradient;
    private Drawable lockDrawable;

    private int starBgColor;
    private Drawable starFg, starBg;

    public boolean drawAvatarOverlays(Canvas canvas) {
        boolean needInvalidate = false, stars = false;
        if (chat != null && (chat.flags2 & 2048) != 0) {
            stars = true;
            float bottom =  avatarImage.getImageY2();
            float right = avatarImage.getImageX2();
            float checkProgress = checkBox != null && checkBox.isChecked() ? 1.0f - checkBox.getProgress() : 1.0f;

            if (starBg == null) {
                starBg = getContext().getResources().getDrawable(R.drawable.star_small_outline).mutate();
            }
            final int bg = Theme.getColor(Theme.key_windowBackgroundWhite);
            if (starBgColor != bg) {
                starBg.setColorFilter(new PorterDuffColorFilter(starBgColor = bg, PorterDuff.Mode.SRC_IN));
            }
            if (starFg == null) {
                starFg = getContext().getResources().getDrawable(R.drawable.star_small_inner).mutate();
            }

            final int sz = dp(19.33f);
            AndroidUtilities.rectTmp2.set((int) right + dp(1.66f) - sz, (int) bottom - sz, (int) right + dp(1.66f), (int) bottom);
            AndroidUtilities.rectTmp2.inset(-dp(1), -dp(1));
            starBg.setBounds(AndroidUtilities.rectTmp2);
            starBg.setAlpha((int) (0xFF * checkProgress));
            starBg.draw(canvas);
            AndroidUtilities.rectTmp2.set((int) right + dp(1.66f) - sz, (int) bottom - sz, (int) right + dp(1.66f), (int) bottom);
            starFg.setBounds(AndroidUtilities.rectTmp2);
            starFg.setAlpha((int) (0xFF * checkProgress));
            starFg.draw(canvas);
        }
        if (isDialogCell) {
            showTtl = ttlPeriod > 0 && !storyParams.drawnLive;
            if (showTtl || ttlProgress > 0) {
                if (timerDrawable == null || (timerDrawable.getTime() != ttlPeriod && ttlPeriod > 0)) {
                    timerDrawable = TimerDrawable.getTtlIconForDialogs(ttlPeriod);
                }
                if (timerPaint == null) {
                    timerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                    timerPaint2 = new Paint(Paint.ANTI_ALIAS_FLAG);
                    timerPaint2.setColor(0x32000000);
                }
                int top = (int) (avatarImage.getImageY2() - dp(9));
                int left;
                if (LocaleController.isRTL) {
                    left = (int) (storyParams.originalAvatarRect.left + dp(9));
                } else {
                    left = (int) (storyParams.originalAvatarRect.right - dp(9));
                }
                timerDrawable.setBounds(
                        0, 0, dp(22), dp(22)
                );

                timerDrawable.setTime(ttlPeriod);
                if (avatarImage.updateThumbShaderMatrix()) {
                    if (avatarImage.thumbShader != null) {
                        timerPaint.setShader(avatarImage.thumbShader);
                    } else if (avatarImage.staticThumbShader != null) {
                        timerPaint.setShader(avatarImage.staticThumbShader);
                    }
                } else {
                    timerPaint.setShader(null);
                    if (avatarImage.getBitmap() != null && !avatarImage.getBitmap().isRecycled()) {
                        timerPaint.setColor(AndroidUtilities.getDominantColor(avatarImage.getBitmap()));
                    } else if (avatarImage.getDrawable() instanceof VectorAvatarThumbDrawable){
                        VectorAvatarThumbDrawable vectorAvatarThumbDrawable = (VectorAvatarThumbDrawable) avatarImage.getDrawable();
                        timerPaint.setColor(vectorAvatarThumbDrawable.gradientTools.getAverageColor());
                    } else {
                        timerPaint.setColor(avatarDrawable.getColor2());
                    }
                }
                canvas.save();
                float s = ttlProgress;
                if (checkBox != null) {
                    s *= 1f - checkBox.getProgress();
                }
                canvas.scale(s, s, left, top);
                canvas.drawCircle(left, top, AndroidUtilities.dpf2(11f), timerPaint);
                canvas.drawCircle(left, top, AndroidUtilities.dpf2(11f), timerPaint2);
                canvas.save();
                canvas.translate(left - AndroidUtilities.dpf2(11f), top - AndroidUtilities.dpf2(11f));
                timerDrawable.draw(canvas);
                canvas.restore();
                canvas.restore();
            }

            if (showTtl) {
                if (ttlProgress < 1.0f) {
                    ttlProgress += 16f / 150.0f;
                    needInvalidate = true;
                }
            } else {
                if (ttlProgress > 0.0f) {
                    ttlProgress -= 16f / 150.0f;
                    needInvalidate = true;
                }
            }
            ttlProgress = Utilities.clamp(ttlProgress, 1f, 0);
        }
        return needInvalidate;
    }

    public void onReorderStateChanged(boolean reordering, boolean animated) {
        if (!getIsPinned() && reordering || drawReorder == reordering) {
            if (!getIsPinned()) {
                drawReorder = false;
            }
            return;
        }
        drawReorder = reordering;
        if (animated) {
            reorderIconProgress = drawReorder ? 0.0f : 1.0f;
        } else {
            reorderIconProgress = drawReorder ? 1.0f : 0.0f;
        }
        invalidate();
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    @Override
    public boolean performAccessibilityAction(int action, Bundle arguments) {
        if (action == R.id.acc_action_chat_preview && parentFragment != null) {
            parentFragment.showChatPreview(this);
            return true;
        }
        return super.performAccessibilityAction(action, arguments);
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.addAction(AccessibilityNodeInfo.ACTION_CLICK);
        info.addAction(AccessibilityNodeInfo.ACTION_LONG_CLICK);
        if (parentFragment != null) {
            info.addAction(new AccessibilityNodeInfo.AccessibilityAction(R.id.acc_action_chat_preview, getString(R.string.AccActionChatPreview)));
        }
        if (checkBox != null && checkBox.isChecked()) {
            info.setClassName("android.widget.CheckBox");
            info.setCheckable(true);
            info.setChecked(true);
        }
    }

    @Override
    public void onPopulateAccessibilityEvent(AccessibilityEvent event) {
        super.onPopulateAccessibilityEvent(event);
        StringBuilder sb = new StringBuilder();
        if (titleOverride != null) {
            sb.append(titleOverride);
            sb.append(". ");
        } else {
            if (chat != null) {
                if (chat.broadcast) {
                    sb.append(getString(R.string.AccDescrChannel));
                } else {
                    sb.append(getString(R.string.AccDescrGroup));
                }
                sb.append(". ");
                sb.append(chat.title);
                sb.append(". ");
            }
        }
        if (drawVerified) {
            sb.append(getString(R.string.AccDescrVerified));
            sb.append(". ");
        }
        if (dialogMuted) {
            sb.append(getString(R.string.AccDescrNotificationsMuted));
            sb.append(". ");
        }
        if (message == null) {
            event.setContentDescription(sb);
            setContentDescription(sb);
            return;
        }
        int lastDate = lastMessageDate;
        if (lastMessageDate == 0) {
            lastDate = message.messageOwner.date;
        }
        String date = LocaleController.formatDateAudio(lastDate, true);
        if (message.isOut()) {
            sb.append(LocaleController.formatString("AccDescrSentDate", R.string.AccDescrSentDate, date));
        } else {
            sb.append(LocaleController.formatString("AccDescrReceivedDate", R.string.AccDescrReceivedDate, date));
        }
        sb.append(". ");
        if (chat != null && !message.isOut() && message.isFromUser() && message.messageOwner.action == null) {
            TLRPC.User fromUser = MessagesController.getInstance(currentAccount).getUser(message.messageOwner.from_id.user_id);
            if (fromUser != null) {
                sb.append(ContactsController.formatName(fromUser.first_name, fromUser.last_name));
                sb.append(". ");
            }
        }
        event.setContentDescription(sb);
        setContentDescription(sb);
    }

    private MessageObject getCaptionMessage() {
        if (groupMessages == null) {
            if (message != null && message.caption != null) {
                return message;
            }
            return null;
        }

        MessageObject captionMessage = null;
        int hasCaption = 0;
        for (int i = 0; i < groupMessages.size(); ++i) {
            MessageObject msg = groupMessages.get(i);
            if (msg != null && msg.caption != null) {
                captionMessage = msg;
                if (!TextUtils.isEmpty(msg.caption)) {
                    hasCaption++;
                }
            }
        }
        if (hasCaption > 1) {
            return null;
        }
        return captionMessage;
    }

    public void updateMessageThumbs() {
        if (message == null) {
            return;
        }
        String restrictionReason = MessagesController.getInstance(message.currentAccount).getRestrictionReason(message.messageOwner.restriction_reason);
        if (message != null && message.messageOwner != null && message.messageOwner.media instanceof TLRPC.TL_messageMediaPaidMedia) {
            thumbsCount = 0;
            hasVideoThumb = false;
            TLRPC.TL_messageMediaPaidMedia paidMedia = (TLRPC.TL_messageMediaPaidMedia) message.messageOwner.media;
            int index = 0;
            for (int i = 0; i < paidMedia.extended_media.size() && thumbsCount < 3; ++i) {
                TLRPC.MessageExtendedMedia emedia = paidMedia.extended_media.get(i);
                if (emedia instanceof TLRPC.TL_messageExtendedMediaPreview) {
                    setThumb(index++, ((TLRPC.TL_messageExtendedMediaPreview) emedia).thumb);
                } else if (emedia instanceof TLRPC.TL_messageExtendedMedia) {
                    setThumb(index++, ((TLRPC.TL_messageExtendedMedia) emedia).media);
                }
            }
        } else if (groupMessages != null && groupMessages.size() > 1 && TextUtils.isEmpty(restrictionReason)) {
            thumbsCount = 0;
            hasVideoThumb = false;
            Collections.sort(groupMessages, Comparator.comparingInt(MessageObject::getId));
            for (int i = 0; i < Math.min(3, groupMessages.size()); ++i) {
                MessageObject message = groupMessages.get(i);
                if (message != null && !message.needDrawBluredPreview() && (message.isPhoto() || message.isNewGif() || message.isVideo() || message.isRoundVideo() || message.isStoryMedia())) {
                    String type = message.isWebpage() ? message.messageOwner.media.webpage.type : null;
                    if (!("app".equals(type) || "profile".equals(type) || "article".equals(type) || type != null && type.startsWith("telegram_"))) {
                        setThumb(i, message);
                    }
                }
            }
        } else if (message != null) {
            thumbsCount = 0;
            hasVideoThumb = false;
            if (!message.needDrawBluredPreview() && (message.isPhoto() || message.isNewGif() || message.isVideo() || message.isRoundVideo() || message.isStoryMedia())) {
                String type = message.isWebpage() ? message.messageOwner.media.webpage.type : null;
                if (!("app".equals(type) || "profile".equals(type) || "article".equals(type) || type != null && type.startsWith("telegram_"))) {
                    setThumb(0, message);
                }
            }
        }
    }

    private void setThumb(int index, MessageObject message) {
        ArrayList<TLRPC.PhotoSize> photoThumbs = message.photoThumbs;
        TLObject photoThumbsObject = message.photoThumbsObject;
        if (message.isStoryMedia()) {
            TL_stories.StoryItem storyItem = message.messageOwner.media.storyItem;
            if (storyItem != null && storyItem.media != null) {
                if (storyItem.media.document != null) {
                    photoThumbs = storyItem.media.document.thumbs;
                    photoThumbsObject = storyItem.media.document;
                } else if (storyItem.media.photo != null) {
                    photoThumbs = storyItem.media.photo.sizes;
                    photoThumbsObject = storyItem.media.photo;
                }
            } else {
                return;
            }
        }

        TLRPC.PhotoSize smallThumb = FileLoader.getStrippedPhotoSize(photoThumbs);
        if (smallThumb == null) {
            smallThumb = FileLoader.getClosestPhotoSizeWithSize(photoThumbs, 40);
        }
        TLRPC.PhotoSize bigThumb = FileLoader.getClosestPhotoSizeWithSize(photoThumbs, AndroidUtilities.getPhotoSize(), false, null, true);
        if (smallThumb == bigThumb) {
            bigThumb = null;
        }
        TLRPC.PhotoSize selectedThumb = bigThumb;
        if (selectedThumb == null || !DownloadController.getInstance(currentAccount).canDownloadMedia(message)) {
            selectedThumb = smallThumb;
        }

        if (smallThumb != null) {
            hasVideoThumb = hasVideoThumb || (message.isVideo() || message.isRoundVideo());
            if (thumbsCount < 3) {
                thumbsCount++;
                drawPlay[index] = (message.isVideo() || message.isRoundVideo()) && !message.hasMediaSpoilers();
                drawSpoiler[index] = message.hasMediaSpoilers();
                int size = message.type == MessageObject.TYPE_PHOTO && selectedThumb != null ? selectedThumb.size : 0;
                String filter = message.hasMediaSpoilers() ? "5_5_b" : "20_20";
                thumbImage[index].setImage(ImageLocation.getForObject(selectedThumb, photoThumbsObject), filter, ImageLocation.getForObject(smallThumb, photoThumbsObject), filter, size, null, message, 0);
                thumbImage[index].setRoundRadius(message.isRoundVideo() ? dp(18) : dp(2));
                needEmoji = false;
            }
        }
    }

    private void setThumb(int index, TLRPC.MessageMedia media) {
        TLObject object = null;
        ArrayList<TLRPC.PhotoSize> photoThumbs = null;
        boolean isVideo = false;
        if (media instanceof TLRPC.TL_messageMediaPhoto) {
            object = media.photo;
            photoThumbs = media.photo.sizes;
        } else if (media instanceof TLRPC.TL_messageMediaDocument) {
            isVideo = MessageObject.isVideoDocument(media.document);
            object = media.document;
            photoThumbs = media.document.thumbs;
        }

        TLRPC.PhotoSize smallThumb = FileLoader.getStrippedPhotoSize(photoThumbs);
        if (smallThumb == null) {
            smallThumb = FileLoader.getClosestPhotoSizeWithSize(photoThumbs, 40);
        }
        TLRPC.PhotoSize bigThumb = FileLoader.getClosestPhotoSizeWithSize(photoThumbs, AndroidUtilities.getPhotoSize(), false, null, true);
        if (smallThumb == bigThumb) {
            bigThumb = null;
        }
        TLRPC.PhotoSize selectedThumb = bigThumb;
        if (selectedThumb == null || !DownloadController.getInstance(currentAccount).canDownloadMedia(DownloadController.AUTODOWNLOAD_TYPE_PHOTO, (long) selectedThumb.size)) {
            selectedThumb = smallThumb;
        }

        if (smallThumb != null) {
            hasVideoThumb = hasVideoThumb || isVideo;
            if (thumbsCount < 3) {
                thumbsCount++;
                drawPlay[index] = isVideo;
                drawSpoiler[index] = false;
                int size = !isVideo && selectedThumb != null ? selectedThumb.size : 0;
                String filter = "20_20";
                thumbImage[index].setImage(ImageLocation.getForObject(selectedThumb, object), filter, ImageLocation.getForObject(smallThumb, object), filter, size, null, message, 0);
                thumbImage[index].setRoundRadius(dp(2));
                needEmoji = false;
            }
        }
    }

    private void setThumb(int index, TLRPC.PhotoSize thumb) {
        if (index >= 3) return;
        if (thumb != null) {
            hasVideoThumb = false;
            if (thumbsCount < 3) {
                thumbsCount++;
                drawPlay[index] = false;
                drawSpoiler[index] = true;
                int size = 0;
                String filter = "2_2_b";
                thumbImage[index].setImage(ImageLocation.getForObject(thumb, message.messageOwner), filter, null, null, size, null, message, 0);
                thumbImage[index].setRoundRadius(dp(2));
                needEmoji = false;
            }
        }
    }

    public String getMessageNameString() {
        if (message == null) {
            return null;
        }
        TLRPC.User user;
        TLRPC.User fromUser = null;
        TLRPC.Chat fromChat = null;
        long fromId = message.getFromChatId();
        final long selfId = UserConfig.getInstance(currentAccount).getClientUserId();
        if (currentDialogId == selfId) {
            long savedDialogId = message.getSavedDialogId();
            if (savedDialogId == selfId) {
                return null;
            } else if (savedDialogId != UserObject.ANONYMOUS) {
                if (message.messageOwner != null && message.messageOwner.fwd_from != null) {
                    long fwdId = DialogObject.getPeerDialogId(message.messageOwner.fwd_from.saved_from_id);
                    if (fwdId == 0) {
                        fwdId = DialogObject.getPeerDialogId(message.messageOwner.fwd_from.from_id);
                    }
                    if (fwdId > 0 && fwdId != savedDialogId) {
                        return null;
                    }
                }
                fromId = savedDialogId;
            }
        }
        if (DialogObject.isUserDialog(fromId)) {
            fromUser = MessagesController.getInstance(currentAccount).getUser(fromId);
        } else {
            fromChat = MessagesController.getInstance(currentAccount).getChat(-fromId);
        }

        if (currentDialogId == selfId) {
            if (fromUser != null) {
                return AndroidUtilities.escape(UserConfig.getChatTitleOverride(currentAccount, fromUser.id, UserObject.getFirstName(fromUser).replace("\n", "")));
            } else if (fromChat != null) {
                return AndroidUtilities.escape(UserConfig.getChatTitleOverride(currentAccount, fromChat.id, fromChat.title.replace("\n", "")));
            }
            return null;
        } else if (currentDialogId == UserObject.VERIFY && message != null && message.messageOwner != null && message.messageOwner.fwd_from != null) {
            if (message.messageOwner.fwd_from.from_name != null) {
                return AndroidUtilities.escape(message.messageOwner.fwd_from.from_name);
            } else {
                long did = DialogObject.getPeerDialogId(message.messageOwner.fwd_from.from_id);
                if (DialogObject.isUserDialog(did)) {
                    fromUser = MessagesController.getInstance(currentAccount).getUser(did);
                    return UserObject.getUserName(fromUser);
                } else {
                    fromChat = MessagesController.getInstance(currentAccount).getChat(-did);
                    return fromChat == null ? "" : fromChat.title;
                }
            }
        } else if (message.isOutOwner() && fromUser != null) {
            return getString(R.string.FromYou);
        } else if (message != null && message.messageOwner != null && message.messageOwner.from_id instanceof TLRPC.TL_peerUser && (user = MessagesController.getInstance(currentAccount).getUser(message.messageOwner.from_id.user_id)) != null) {
            return AndroidUtilities.escape(UserConfig.getChatTitleOverride(currentAccount, user.id, UserObject.getFirstName(user).replace("\n", "")));
        } else if (message != null && message.messageOwner != null && message.messageOwner.fwd_from != null && message.messageOwner.fwd_from.from_name != null) {
            return AndroidUtilities.escape(UserConfig.getChatTitleOverride(currentAccount, message.messageOwner.fwd_from.from_id, message.messageOwner.fwd_from.from_name));
        } else if (fromUser != null) {
            if (useForceThreeLines || SharedConfig.useThreeLinesLayout) {
                if (UserObject.isDeleted(fromUser)) {
                    return getString(R.string.HiddenName);
                } else {
                    return AndroidUtilities.escape(UserConfig.getChatTitleOverride(currentAccount, fromUser.id, ContactsController.formatName(fromUser.first_name, fromUser.last_name).replace("\n", "")));
                }
            } else {
                return AndroidUtilities.escape(UserConfig.getChatTitleOverride(currentAccount, fromUser.id, UserObject.getFirstName(fromUser).replace("\n", "")));
            }
        } else if (fromChat != null && fromChat.title != null) {
            return AndroidUtilities.escape(UserConfig.getChatTitleOverride(currentAccount, fromChat.id, fromChat.title.replace("\n", "")));
        } else {
            return "DELETED";
        }
    }

    public SpannableStringBuilder getMessageStringFormatted(int messageFormatType, String restrictionReason, CharSequence messageNameString, boolean applyThumbs) {
        SpannableStringBuilder stringBuilder;
        MessageObject captionMessage = getCaptionMessage();
        CharSequence msgText = message != null ? message.messageText : null;
        applyName = true;
        if (!TextUtils.isEmpty(restrictionReason)) {
            stringBuilder = formatInternal(messageFormatType, restrictionReason, messageNameString);
        } else if (message.messageOwner instanceof TLRPC.TL_messageService) {
            CharSequence mess;
            if (message.messageTextShort != null) {
                mess = message.messageTextShort;
            } else {
                mess = message.messageText;
            }
            if (MessageObject.isTopicActionMessage(message)) {
                stringBuilder = formatInternal(messageFormatType, mess, messageNameString);
                if (message.topicIconDrawable[0] instanceof ForumBubbleDrawable) {
                    TLRPC.TL_forumTopic topic = MessagesController.getInstance(currentAccount).getTopicsController().findTopic(-message.getDialogId(), MessageObject.getTopicId(currentAccount, message.messageOwner, true));
                    if (topic != null) {
                        ((ForumBubbleDrawable) message.topicIconDrawable[0]).setColor(topic.icon_color);
                    }
                }
            } else {
                applyName = false;
                stringBuilder = SpannableStringBuilder.valueOf(mess);
            }
            if (applyThumbs) {
                applyThumbs(stringBuilder);
            }
        } else if (captionMessage != null && captionMessage.caption != null) {
            MessageObject message = captionMessage;
            CharSequence mess = message.caption.toString();
            String emoji;
            if (!needEmoji) {
                emoji = "";
            } else if (message.isVideo()) {
                emoji = "\uD83D\uDCF9 ";
            } else if (message.isVoice()) {
                emoji = "\uD83C\uDFA4 ";
            } else if (message.isMusic()) {
                emoji = "\uD83C\uDFA7 ";
            } else if (message.isPhoto()) {
                emoji = "\uD83D\uDDBC ";
            } else {
                emoji = "\uD83D\uDCCE ";
            }
            if (message.hasHighlightedWords() && !TextUtils.isEmpty(message.messageOwner.message)) {
                CharSequence text = message.messageTrimmedToHighlight;
                int w = getMeasuredWidth() - dp(messagePaddingStart + 23 + 24);
                if (hasNameInMessage) {
                    if (!TextUtils.isEmpty(messageNameString)) {
                        w -= currentMessagePaint.measureText(messageNameString.toString());
                    }
                    w -= currentMessagePaint.measureText(": ");
                }
                if (w > 0 && message.messageTrimmedToHighlightCut) {
                    text = AndroidUtilities.ellipsizeCenterEnd(text, message.highlightedWords.get(0), w, currentMessagePaint, 130).toString();
                }
                stringBuilder = new SpannableStringBuilder(emoji).append(text);
            } else {
                if (mess.length() > 150) {
                    mess = mess.subSequence(0, 150);
                }
                SpannableString msgBuilder = new SpannableString(mess);
                if (message != null) {
                    message.spoilLoginCode();
                }
                MediaDataController.addTextStyleRuns(message.messageOwner.entities, mess, msgBuilder, TextStyleSpan.FLAG_STYLE_SPOILER | TextStyleSpan.FLAG_STYLE_STRIKE);
                if (message != null && message.messageOwner != null) {
                    MediaDataController.addAnimatedEmojiSpans(message.messageOwner.entities, msgBuilder, currentMessagePaint == null ? null : currentMessagePaint.getFontMetricsInt());
                }
                CharSequence charSequence = new SpannableStringBuilder(emoji).append(AndroidUtilities.replaceNewLines(msgBuilder));
                if (applyThumbs) {
                    charSequence = applyThumbs(charSequence);
                }
                stringBuilder = formatInternal(messageFormatType, charSequence, messageNameString);
            }
        } else if (message.messageOwner.media != null && !message.isMediaEmpty()) {
            currentMessagePaint = Theme.dialogs_messagePrintingPaint[paintIndex];
            CharSequence innerMessage;
            int colorKey = Theme.key_chats_attachMessage;
            if (message.messageOwner.media instanceof TLRPC.TL_messageMediaPoll) {
                TLRPC.TL_messageMediaPoll mediaPoll = (TLRPC.TL_messageMediaPoll) message.messageOwner.media;
                if (mediaPoll.poll.question != null && mediaPoll.poll.question.entities != null) {
                    SpannableString questionText = new SpannableString(mediaPoll.poll.question.text.replace('\n', ' '));
                    MediaDataController.addTextStyleRuns(mediaPoll.poll.question.entities, mediaPoll.poll.question.text, questionText);
                    MediaDataController.addAnimatedEmojiSpans(mediaPoll.poll.question.entities, questionText, Theme.dialogs_messagePaint[paintIndex].getFontMetricsInt());
                    innerMessage = new SpannableStringBuilder("\uD83D\uDCCA \u2068").append(questionText).append("\u2069");
                } else {
                    innerMessage = String.format("\uD83D\uDCCA \u2068%s\u2069", mediaPoll.poll.question.text);
                }
            } else if (message.messageOwner.media instanceof TLRPC.TL_messageMediaToDo) {
                TLRPC.TL_messageMediaToDo mediaTodo = (TLRPC.TL_messageMediaToDo) message.messageOwner.media;
                if (mediaTodo.todo.title != null && mediaTodo.todo.title.entities != null) {
                    SpannableString questionText = new SpannableString(mediaTodo.todo.title.text.replace('\n', ' '));
                    MediaDataController.addTextStyleRuns(mediaTodo.todo.title.entities, mediaTodo.todo.title.text, questionText);
                    MediaDataController.addAnimatedEmojiSpans(mediaTodo.todo.title.entities, questionText, Theme.dialogs_messagePaint[paintIndex].getFontMetricsInt());
                    innerMessage = new SpannableStringBuilder("\u2705 \u2068").append(questionText).append("\u2069");
                } else {
                    innerMessage = String.format("\u2705 \u2068%s\u2069", mediaTodo.todo.title.text);
                }
            } else if (message.messageOwner.media instanceof TLRPC.TL_messageMediaGame) {
                innerMessage = String.format("\uD83C\uDFAE \u2068%s\u2069", message.messageOwner.media.game.title);
            } else if (message.messageOwner.media instanceof TLRPC.TL_messageMediaInvoice) {
                innerMessage = message.messageOwner.media.title;
            } else if (message.type == MessageObject.TYPE_MUSIC) {
                innerMessage = String.format("\uD83C\uDFA7 \u2068%s - %s\u2069", message.getMusicAuthor(), message.getMusicTitle());
            } else if (message.messageOwner.media instanceof TLRPC.TL_messageMediaPaidMedia) {
                TLRPC.TL_messageMediaPaidMedia paidMedia = (TLRPC.TL_messageMediaPaidMedia) message.messageOwner.media;
                final int count = paidMedia.extended_media.size();
                if (hasVideoThumb) {
                    innerMessage = count > 1 ? LocaleController.formatPluralString("Media", count) : getString(R.string.AttachVideo);
                } else {
                    innerMessage = count > 1 ? LocaleController.formatPluralString("Photos", count) : getString(R.string.AttachPhoto);
                }
                innerMessage = StarsIntroActivity.replaceStars(LocaleController.formatString(R.string.AttachPaidMedia, innerMessage));
                colorKey = Theme.key_chats_actionMessage;
            } else if (thumbsCount > 1) {
                if (hasVideoThumb) {
                    innerMessage = LocaleController.formatPluralString("Media", groupMessages == null ? 0 : groupMessages.size());
                } else {
                    innerMessage = LocaleController.formatPluralString("Photos", groupMessages == null ? 0 : groupMessages.size());
                }
                colorKey = Theme.key_chats_actionMessage;
            } else {
                innerMessage = msgText.toString();
                colorKey = Theme.key_chats_actionMessage;
            }
            if (innerMessage instanceof String) {
                innerMessage = ((String) innerMessage).replace('\n', ' ');
            }
            CharSequence message = innerMessage;
            if (applyThumbs) {
                message = applyThumbs(innerMessage);
            }
            stringBuilder = formatInternal(messageFormatType, message, messageNameString);
            if (!isForumCell()) {
                try {
                    stringBuilder.setSpan(new ForegroundColorSpanThemable(colorKey, resourcesProvider), hasNameInMessage ? messageNameString.length() + 2 : 0, stringBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        } else if (message.messageOwner.message != null) {
            CharSequence mess = message.messageOwner.message;
            if (message.hasHighlightedWords()) {
                if (message.messageTrimmedToHighlight != null) {
                    mess = message.messageTrimmedToHighlight;
                }
                int w = getMeasuredWidth() - dp(messagePaddingStart + 23 + 10);
                if (hasNameInMessage) {
                    if (!TextUtils.isEmpty(messageNameString)) {
                        w -= currentMessagePaint.measureText(messageNameString.toString());
                    }
                    w -= currentMessagePaint.measureText(": ");
                }
                if (w > 0) {
                    mess = AndroidUtilities.ellipsizeCenterEnd(mess, message.highlightedWords.get(0), w, currentMessagePaint, 130).toString();
                }
            } else {
                if (mess.length() > 150) {
                    mess = mess.subSequence(0, 150);
                }
                mess = AndroidUtilities.replaceNewLines(mess);
            }
            mess = new SpannableString(mess);
            if (message != null) {
                message.spoilLoginCode();
            }
            MediaDataController.addTextStyleRuns(message, (Spannable) mess, TextStyleSpan.FLAG_STYLE_SPOILER | TextStyleSpan.FLAG_STYLE_STRIKE);
            if (message != null && message.messageOwner != null) {
                MediaDataController.addAnimatedEmojiSpans(message.messageOwner.entities, mess, currentMessagePaint == null ? null : currentMessagePaint.getFontMetricsInt());
            }
            if (applyThumbs) {
                mess = applyThumbs(mess);
            }
            stringBuilder = formatInternal(messageFormatType, mess, messageNameString);
        } else {
            stringBuilder = new SpannableStringBuilder();
        }
        return stringBuilder;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (storyParams.checkOnTouchEvent(ev, this)) {
            return true;
        }
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_UP || ev.getAction() == MotionEvent.ACTION_CANCEL) {
            storyParams.checkOnTouchEvent(ev, this);
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (storyParams.checkOnTouchEvent(event, this)) {
            return true;
        }
        if (delegate == null || delegate.canClickButtonInside()) {
            if (lastTopicMessageUnread && canvasButton != null && buttonLayout != null && canvasButton.checkTouchEvent(event)) {
                return true;
            }
        }
        return super.onTouchEvent(event);
    }


    public void setClipProgress(float value) {
        clipProgress = value;
        invalidate();
    }

    public float getClipProgress() {
        return clipProgress;
    }

    public void setTopClip(int value) {
        topClip = value;
    }

    public void setBottomClip(int value) {
        bottomClip = value;
    }

    public MessageObject getMessage() {
        return message;
    }

    public void setDialogCellDelegate(SavedChannelCellDelegate delegate) {
        this.delegate = delegate;
    }

    public interface SavedChannelCellDelegate {
        void onButtonClicked(SavedChannelCell dialogCell);
        void onButtonLongPress(SavedChannelCell dialogCell);
        boolean canClickButtonInside();
        void openStory(SavedChannelCell dialogCell, Runnable onDone);
        void showChatPreview(SavedChannelCell dialogCell);
        void openHiddenStories();
    }

    private class DialogUpdateHelper {

        public long lastDrawnDialogId;
        public long lastDrawnMessageId;
        public boolean lastDrawnTranslated;
        public Integer lastDrawnPrintingType;
        public int lastDrawnSizeHash;
        public int lastTopicsCount;
        public boolean lastDrawnPinned;
        public boolean lastDrawnHasCall;


        public float typingProgres;
        public boolean typingOutToTop;
        public int lastKnownTypingType;
        boolean waitngNewMessageFroTypingAnimation = false;
        long startWaitingTime;


        public boolean update() {
            CharSequence currentTitle = SavedChannelCell.this.nameLayout != null ? SavedChannelCell.this.nameLayout.getText() : null;
            int messageHash = message == null ? 0 : message.getId() + message.hashCode();
            Integer printingType = null;

            if (!isForumCell() && isDialogCell) {
                if (!TextUtils.isEmpty(MessagesController.getInstance(currentAccount).getPrintingString(currentDialogId, getTopicId(), true))) {
                    printingType = MessagesController.getInstance(currentAccount).getPrintingStringType(currentDialogId, getTopicId());
                } else {
                    printingType = null;
                }
            }
            int sizeHash = getMeasuredWidth() + (getMeasuredHeight() << 16);
            int topicCount = 0;
            if (isForumCell()) {
                ArrayList<TLRPC.TL_forumTopic> topics = MessagesController.getInstance(currentAccount).getTopicsController().getTopics(-currentDialogId);
                topicCount = topics == null ? -1 : topics.size();
                if (topicCount == -1 && MessagesController.getInstance(currentAccount).getTopicsController().endIsReached(-currentDialogId)) {
                    topicCount = 0;
                }
            }

            boolean hasCall = chat != null && chat.call_active && chat.call_not_empty;
            boolean translated = MessagesController.getInstance(currentAccount).getTranslateController().isTranslatingDialog(currentDialogId);
            if (lastDrawnSizeHash == sizeHash &&
                    lastDrawnMessageId == messageHash &&
                    lastDrawnTranslated == translated &&
                    lastDrawnDialogId == currentDialogId &&
                    Objects.equals(lastDrawnPrintingType, printingType) &&
                    lastTopicsCount == topicCount &&
                    lastDrawnPinned == drawPin &&
                    lastDrawnHasCall == hasCall) {
                return false;
            }

            if (lastDrawnDialogId != currentDialogId) {
                typingProgres = printingType == null ? 0f : 1f;
                waitngNewMessageFroTypingAnimation = false;
            } else {
                if (!Objects.equals(lastDrawnPrintingType, printingType) || waitngNewMessageFroTypingAnimation) {
                    if (!waitngNewMessageFroTypingAnimation && printingType == null) {
                        waitngNewMessageFroTypingAnimation = true;
                        startWaitingTime = System.currentTimeMillis();
                    } else if (waitngNewMessageFroTypingAnimation && lastDrawnMessageId != messageHash) {
                        waitngNewMessageFroTypingAnimation = false;
                    }
                    if (lastDrawnMessageId != messageHash) {
                        typingOutToTop = false;
                    } else {
                        typingOutToTop = true;
                    }
                }
            }
            if (printingType != null) {
                lastKnownTypingType = printingType;
            }
            lastDrawnDialogId = currentDialogId;
            lastDrawnMessageId = messageHash;
            lastDrawnPrintingType = printingType;
            lastDrawnSizeHash = sizeHash;
            lastTopicsCount = topicCount;
            lastDrawnPinned = drawPin;
            lastDrawnHasCall = hasCall;
            lastDrawnTranslated = translated;

            return true;
        }

        public void updateAnimationValues() {
            if (!waitngNewMessageFroTypingAnimation) {
                if (lastDrawnPrintingType != null && typingLayout != null && typingProgres != 1f) {
                    typingProgres += 16f / 200f;
                    invalidate();
                } else if (lastDrawnPrintingType == null && typingProgres != 0) {
                    typingProgres -= 16f / 200f;
                    invalidate();
                }
                typingProgres = Utilities.clamp(typingProgres, 1f, 0f);
            } else {
                if (System.currentTimeMillis() - startWaitingTime > 100) {
                    waitngNewMessageFroTypingAnimation = false;
                }
                invalidate();
            }
        }
    }

    @Override
    public void invalidate() {
        if (StoryViewer.animationInProgress) {
            return;
        }
        super.invalidate();
    }

    @Override
    public void invalidate(int l, int t, int r, int b) {
        if (StoryViewer.animationInProgress) {
            return;
        }
        super.invalidate(l, t, r, b);
    }

    private static class ForumFormattedNames {
        private final SavedChannelCell parent;
        int lastMessageId;

        int topMessageTopicStartIndex;
        int topMessageTopicEndIndex;
        boolean lastTopicMessageUnread;
        boolean isLoadingState;
        HashMap<Long, AvatarSpan> avatarSpans;
        CharSequence formattedNames;

        ForumFormattedNames(SavedChannelCell parent) {
            this.parent = parent;
        }

        private void invalidateSpans() {
            if (avatarSpans == null || avatarSpans.isEmpty()) {
                return;
            }

            for (Map.Entry<Long, AvatarSpan> entry: avatarSpans.entrySet()) {
                entry.getValue().setDialogId(entry.getKey());
            }
        }

        private void formatTopicsNames(int currentAccount, MessageObject message, TLRPC.Chat chat) {
            int messageId = message == null || chat == null ? 0 : message.getId();
            if (lastMessageId == messageId && !isLoadingState) {
                return;
            }
            avatarSpans = null;
            topMessageTopicStartIndex = 0;
            topMessageTopicEndIndex = 0;
            lastTopicMessageUnread = false;
            isLoadingState = false;
            lastMessageId = messageId;
            Paint currentMessagePaint = Theme.dialogs_messagePaint[0];
            if (chat != null) {
                List<TLRPC.TL_forumTopic> topics = MessagesController.getInstance(currentAccount).getTopicsController().getTopics(chat.id);

                boolean hasDivider = false;
                if (topics != null && !topics.isEmpty()) {
                    topics = new ArrayList<>(topics);
                    Collections.sort(topics, Comparator.comparingInt(o -> -o.top_message));
                    SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder();
                    long topMessageTopicId = 0;
                    int boldLen = 0;
                    if (message != null && !ChatObject.isMonoForum(chat)) {
                        topMessageTopicId = MessageObject.getTopicId(currentAccount, message.messageOwner, true);
                        TLRPC.TL_forumTopic topic = MessagesController.getInstance(currentAccount).getTopicsController().findTopic(chat.id, topMessageTopicId);
                        if (topic != null) {
                            CharSequence topicString = ForumUtilities.getTopicSpannedName(topic, currentMessagePaint, false);
                            spannableStringBuilder.append(topicString);
                            if (topic.unread_count > 0) {
                                boldLen = topicString.length();
                            }
                            topMessageTopicStartIndex = 0;
                            topMessageTopicEndIndex = topicString.length();

                            if (message.isOutOwner()) {
                                lastTopicMessageUnread = false;
                            } else {
                                lastTopicMessageUnread = topic.unread_count > 0;
                            }
                        } else {
                            lastTopicMessageUnread = false;
                        }
                        if (lastTopicMessageUnread) {
                            spannableStringBuilder.append(" ");
                            spannableStringBuilder.setSpan(new SavedChannelCell.FixedWidthSpan(dp(3)), spannableStringBuilder.length() - 1, spannableStringBuilder.length(), 0);
                            hasDivider = true;
                        }
                    }

                    if (ChatObject.isMonoForum(chat)) {
                        avatarSpans = new HashMap<>();
                        for (int i = 0; i < Math.min(4, topics.size()); i++) {
                            if (spannableStringBuilder.length() != 0) {
                                spannableStringBuilder.append("  ");
                            }

                            final long dialogId = DialogObject.getPeerDialogId(topics.get(i).from_id);

                            AvatarSpan avatarSpan = new AvatarSpan(parent, currentAccount);
                            avatarSpan.needDrawShadow = false;
                            avatarSpan.setDialogId(dialogId);
                            avatarSpans.put(dialogId, avatarSpan);

                            SpannableStringBuilder topicString = SpannableStringBuilder.valueOf(DialogObject.getName(dialogId));
                            topicString.insert(0, "  ");
                            topicString.setSpan(avatarSpan, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            spannableStringBuilder.append(topicString);
                        }
                    } else {
                        boolean firstApplay = true;
                        for (int i = 0; i < Math.min(4, topics.size()); i++) {
                            if (topics.get(i).id == topMessageTopicId) {
                                continue;
                            }

                            if (spannableStringBuilder.length() != 0) {
                                if (firstApplay && hasDivider) {
                                    spannableStringBuilder.append(" ");
                                } else {
                                    spannableStringBuilder.append(", ");
                                }
                            }
                            firstApplay = false;
                            CharSequence topicString = ForumUtilities.getTopicSpannedName(topics.get(i), currentMessagePaint, false);
                            spannableStringBuilder.append(topicString);
                        }
                    }
                    if (boldLen > 0) {
                        spannableStringBuilder.setSpan(
                                new TypefaceSpan(AndroidUtilities.bold(), 0, Theme.key_chats_name, null),
                                0, Math.min(spannableStringBuilder.length(), boldLen + 2), 0
                        );
                    }
                    formattedNames = spannableStringBuilder;
                    return;
                }

                if (!MessagesController.getInstance(currentAccount).getTopicsController().endIsReached(chat.id)) {
                    MessagesController.getInstance(currentAccount).getTopicsController().preloadTopics(chat.id);
                    formattedNames = getString(R.string.Loading);
                    isLoadingState = true;
                } else {
                    formattedNames = getString(ChatObject.isMonoForum(chat) ? R.string.NoMonoforumTopicsCreated : R.string.NoTopicsCreated);
                }
            }
        }
    }

    private ColorFilter[] adaptiveEmojiColorFilter;
    private int[] adaptiveEmojiColor;
    private ColorFilter getAdaptiveEmojiColorFilter(int n, int color) {
        if (adaptiveEmojiColorFilter == null) {
            adaptiveEmojiColor = new int[4];
            adaptiveEmojiColorFilter = new ColorFilter[4];
        }
        if (color != adaptiveEmojiColor[n] || adaptiveEmojiColorFilter[n] == null) {
            adaptiveEmojiColorFilter[n] = new PorterDuffColorFilter(adaptiveEmojiColor[n] = color, PorterDuff.Mode.SRC_IN);
        }
        return adaptiveEmojiColorFilter[n];
    }

    @Override
    protected boolean allowCaching() {
        return false;
    }
}
