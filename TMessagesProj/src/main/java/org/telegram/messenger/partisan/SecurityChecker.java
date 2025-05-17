package org.telegram.messenger.partisan;

import android.content.Context;
import android.provider.Settings;

import androidx.core.util.Consumer;

import com.scottyab.rootbeer.RootBeer;

import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

public class SecurityChecker {
    public interface TwoStepVerificationHandler {
        void handle(boolean error, boolean hasPassword);
    }

    private final Context context;
    private final int accountNum;
    private final Consumer<Set<SecurityIssue>> handler;

    private SecurityChecker(Context context, int accountNum, Consumer<Set<SecurityIssue>> handler) {
        this.context = context;
        this.accountNum = accountNum;
        this.handler = handler;
    }

    public static void checkSecurityIssuesAndSave(Context context, int accountNum, boolean force) {
        UserConfig config = UserConfig.getInstance(accountNum);
        if (!config.isClientActivated()) {
            return;
        }
        if (System.currentTimeMillis() - config.lastSecuritySuggestionsShow < (30L * 24L * 60L * 60L * 1000L) && !force) {
            return;
        }

        checkSecurityIssues(context, accountNum, issues -> {
            boolean issuesChanged = !issues.equals(config.currentSecurityIssues);
            config.currentSecurityIssues = issues;
            if (!config.showSecuritySuggestions) {
                if (!config.getIgnoredSecurityIssues().containsAll(config.currentSecurityIssues)
                        && System.currentTimeMillis() - config.lastSecuritySuggestionsShow >= (30L * 24L * 60L * 60L * 1000L)) {
                    config.showSecuritySuggestions = true;
                    config.lastSecuritySuggestionsShow = System.currentTimeMillis();
                }
            } else {
                if (config.getIgnoredSecurityIssues().containsAll(config.currentSecurityIssues)) {
                    config.showSecuritySuggestions = false;
                    config.lastSecuritySuggestionsShow = System.currentTimeMillis();
                }
            }
            config.saveConfig(false);
            if (config.showSecuritySuggestions) {
                NotificationCenter.getInstance(accountNum).postNotificationName(NotificationCenter.newSuggestionsAvailable);
            }
            if (issuesChanged) {
                NotificationCenter.getInstance(accountNum).postNotificationName(NotificationCenter.securityIssuesChanged);
            }
        });
    }

    public static void checkSecurityIssues(Context context, int accountNum, Consumer<Set<SecurityIssue>> handler) {
        SecurityChecker checker = new SecurityChecker(context, accountNum, handler);
        checker.checkSecurityIssues();
    }

    private void checkSecurityIssues() {
        Set<SecurityIssue> issues = new HashSet<>();
        if (isRooted()) { issues.add(SecurityIssue.ROOT); }
        if (isUsbDebuggingEnabled(context)) { issues.add(SecurityIssue.USB_DEBUGGING); }
        checkTwoStepVerificationEnabled((error, hasPassword) -> {
            if (!error && !hasPassword) {
                issues.add(SecurityIssue.TWO_STEP_VERIFICATION);
            }
            PrivacyChecker.check(accountNum, goodPrivacy -> {
                if (!goodPrivacy) {
                    issues.add(SecurityIssue.PRIVACY);
                }
                handler.accept(issues);
            });
        });
    }

    private boolean isRooted() {
        return new RootBeer(context).isRooted();
    }

    private static boolean isUsbDebuggingEnabled(Context context) {
        return Settings.Secure.getInt(context.getContentResolver(), Settings.Global.ADB_ENABLED, 0) == 1;
    }

    private void checkTwoStepVerificationEnabled(TwoStepVerificationHandler handler) {
        checkTwoStepVerificationEnabled(accountNum, handler);
    }

    public static void checkTwoStepVerificationEnabled(int account, TwoStepVerificationHandler handler) {
    }
}
