package org.telegram.messenger.partisan.ui;

import android.text.Spanned;
import android.text.SpannableStringBuilder;
import android.text.TextPaint;
import android.text.style.ClickableSpan;
import android.view.View;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.Theme;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DidNotGetTheCodeInfo {
    private static final Pattern LINK_PATTERN = Pattern.compile("\\[(.+?)\\]\\((.+?)\\)");

    public static CharSequence build() {
        String steps = LocaleController.getString(isReleaseBuild()
                ? R.string.DidNotGetTheCodePartisanStepsWeb
                : R.string.DidNotGetTheCodePartisanStepsGooglePlay);
        String text = LocaleController.formatString(R.string.DidNotGetTheCodePartisanVersionInfo, steps);
        return AndroidUtilities.replaceTags(replaceLinksWithExternalOnes(text));
    }

    private static boolean isReleaseBuild() {
        return !ApplicationLoader.isStandaloneBuild() && !BuildVars.isBetaApp() && !BuildVars.isAlphaApp();
    }

    private static SpannableStringBuilder replaceLinksWithExternalOnes(String text) {
        SpannableStringBuilder result = new SpannableStringBuilder();
        Matcher matcher = LINK_PATTERN.matcher(text);
        int lastMatchEnd = 0;
        while (matcher.find()) {
            result.append(text, lastMatchEnd, matcher.start());
            String linkText = matcher.group(1);
            String url = matcher.group(2);
            int start = result.length();
            result.append(linkText);
            result.setSpan(new ExternalLinkSpan(url), start, result.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            lastMatchEnd = matcher.end();
        }
        result.append(text, lastMatchEnd, text.length());
        return result;
    }

    private static class ExternalLinkSpan extends ClickableSpan {
        private final String url;

        ExternalLinkSpan(String url) {
            this.url = url;
        }

        @Override
        public void onClick(@NonNull View widget) {
            Browser.openInExternalBrowser(widget.getContext(), url, false);
        }

        @Override
        public void updateDrawState(@NonNull TextPaint ds) {
            ds.setColor(Theme.getColor(Theme.key_dialogTextLink));
            ds.setUnderlineText(false);
        }
    }
}
