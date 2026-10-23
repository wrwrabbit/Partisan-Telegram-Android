package org.telegram.messenger.partisan;

import android.net.Uri;
import android.text.Spannable;
import android.text.TextUtils;
import android.text.style.CharacterStyle;
import android.text.style.URLSpan;
import android.util.Patterns;

import com.google.common.base.Strings;

import org.telegram.messenger.BuildVars;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.browser.Browser;
import org.telegram.messenger.fakepasscode.FakePasscodeUtils;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.LaunchActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;

public class SpoofedLinkChecker {
    public static class SpoofedLinkInfo {
        public boolean isSpoofed = false;
        public String label = null;
    }

    public static class LinkSource {
        private final CharSequence text;
        private final CharacterStyle span;

        private LinkSource(CharSequence text, CharacterStyle span) {
            this.text = text;
            this.span = span;
        }
    }

    private final String url;
    private final Browser.Progress progress;

    private SpoofedLinkChecker(String url, Browser.Progress progress) {
        this.url = url;
        this.progress = progress;
    }

    public static SpoofedLinkInfo isSpoofedLink(String url, Browser.Progress progress) {
        return new SpoofedLinkChecker(url, progress).isSpoofedLinkInternal();
    }

    public static Browser.Progress attachLinkSource(Browser.Progress progress, ChatMessageCell cell, CharacterStyle span) {
        return attachLinkSource(progress, getMessageText(cell), span);
    }

    public static Browser.Progress attachLinkSource(Browser.Progress progress, CharSequence text, CharacterStyle span) {
        if (progress != null) {
            progress.setSpoofedLinkSource(new LinkSource(text, span));
        }
        return progress;
    }

    private SpoofedLinkInfo isSpoofedLinkInternal() {
        try {
            SpoofedLinkInfo result = new SpoofedLinkInfo();

            if (FakePasscodeUtils.isFakePasscodeActivated()) {
                return result;
            }

            LinkSource source = progress != null ? progress.getSpoofedLinkSource() : null;
            if (source == null || !(source.text instanceof Spannable)) {
                return result;
            }
            Spannable spannableText = (Spannable) source.text;

            URLSpan spoofedSpan = getSpans(spannableText, source.span).stream()
                    .filter(span -> isSpoofSpan(spannableText, span))
                    .findAny()
                    .orElse(null);

            if (spoofedSpan == null) {
                return result;
            }

            result.label = getSpanText(spannableText, spoofedSpan).toString();
            result.isSpoofed = true;
            return result;
        } catch (Exception e) {
            if (BuildVars.LOGS_ENABLED) {
                throw e;
            } else {
                return new SpoofedLinkInfo();
            }
        }
    }

    private static CharSequence getMessageText(ChatMessageCell cell) {
        if (cell == null) {
            return null;
        }
        MessageObject messageObject = null;
        if (cell.getCurrentMessagesGroup() != null) {
            MessageObject.GroupedMessages groupedMessages = cell.getCurrentMessagesGroup();
            if (!groupedMessages.messages.isEmpty()) {
                messageObject = groupedMessages.messages.get(0);
            }
        }
        if (messageObject == null) {
            messageObject = cell.getMessageObject();
        }

        if (messageObject == null) {
            return null;
        }
        return messageObject.caption != null ? messageObject.caption : messageObject.messageText;
    }

    private List<URLSpan> getSpans(Spannable spannableText, CharacterStyle sourceSpan) {
        if (sourceSpan instanceof URLSpan) {
            return Collections.singletonList((URLSpan) sourceSpan);
        } else {
            return getSpansByUrl(spannableText);
        }
    }

    private List<URLSpan> getSpansByUrl(Spannable spannableText) {
        URLSpan[] spans = spannableText.getSpans(0, spannableText.length(), URLSpan.class);
        List<URLSpan> matchedSpans = new ArrayList<>();
        for (URLSpan span : spans) {
            String spanUrl = span.getURL();
            if (Objects.equals(spanUrl, url)) {
                matchedSpans.add(span);
            }
        }
        return matchedSpans;
    }

    private static boolean isSpoofSpan(Spannable spannableText, URLSpan span) {
        String label = getSpanText(spannableText, span).toString();

        // avoid ' @username' workaround
        label = label.trim();
        String url = span.getURL();
        if (label.equals(url)) {
            return false;
        }

        boolean isInternalActualLink = Browser.isInternalUrl(url, null);
        if (label.startsWith("@") && !isInternalActualLink) {
            // external website? ok
            return false;
        }

        if (label.startsWith("#")) {
            // hash tags should be EntityType::Hashtag, not CustomUrl
            return true;
        }

        if (isInternalActualLink) {
            boolean isInternalLabel = Browser.isInternalUrl(label, null) || label.startsWith("@");
            return isInternalLabel && !areSameInternalLinks(url, label);
        } else {
            return isUrl(label) && !areSameUrlStrings(url, label);
        }
    }

    private static CharSequence getSpanText(Spannable spannableText, URLSpan span) {
        int start = spannableText.getSpanStart(span);
        int end = spannableText.getSpanEnd(span);
        return spannableText.subSequence(start, end);
    }

    private static boolean areSameInternalLinks(String str1, String str2) {
        String username1 = extractUsername(str1);
        String username2 = extractUsername(str2);
        if (username1 == null && username2 == null) {
            return true;
        }
        // Only usernames are compared. Maybe we need to compare parameters too.
        return username1 != null && username1.equalsIgnoreCase(username2);
    }

    private static String extractUsername(String link) {
        if (link == null || TextUtils.isEmpty(link)) {
            return null;
        }

        if (link.startsWith("@")) {
            return link.substring(1);
        }

        Uri uri = Uri.parse(link);
        List<String> segments = new ArrayList<>(uri.getPathSegments());
        if (!segments.isEmpty() && segments.get(0).equals("s")) {
            segments.remove(0);
        }
        if (!segments.isEmpty()) {
            return segments.get(0);
        }

        Matcher prefixMatcher = LaunchActivity.PREFIX_T_ME_PATTERN.matcher(link);
        if (prefixMatcher.find()) {
            return prefixMatcher.group(1);
        }

        return null;
    }

    private static boolean isUrl(String str) {
        return Patterns.WEB_URL.matcher(str).find();
    }

    private static boolean areSameUrlStrings(String link, String label) {
        Uri linkUri = Uri.parse(link);
        Uri labelUri = Uri.parse(label);

        String linkPath = linkUri.getPath();
        String labelPath = labelUri.getPath();
        String linkHost = linkUri.getHost();
        String labelHost = labelUri.getHost();
        String linkQuery = linkUri.getQuery();
        String labelQuery = labelUri.getQuery();

        if (labelHost == null && labelPath != null) {
            String[] parts = labelPath.split("/");
            labelHost = parts[0];
            if (parts.length == 1) {
                labelPath = null;
            } else {
                labelPath = labelPath.substring(labelHost.length() + 1);
            }
        }

        if (labelPath != null && linkPath == null) {
            return true;
        }
        if (labelPath != null) {
            String trimmedLabelPath = labelPath.replaceAll("/$|^/", "");
            String trimmedLinkPath = linkPath.replaceAll("/$|^/", "");
            if (!trimmedLabelPath.isEmpty() && !trimmedLabelPath.equals(trimmedLinkPath)) {
                return false;
            }
        }

        if (labelUri.getPort() != linkUri.getPort()) {
            return false;
        }

        if (labelHost != null && linkHost != null) {
            if (!labelHost.equalsIgnoreCase(linkHost)) {
                return false;
            }
        } else {
            if (labelHost != null || linkHost != null) {
                return false;
            }
        }

        if (!Strings.isNullOrEmpty(labelQuery) && !labelQuery.equals(linkQuery)) {
            return false;
        }
        return true;
    }
}
