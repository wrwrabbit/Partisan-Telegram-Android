package org.telegram.messenger.partisan;

import static org.telegram.ui.PrivacyControlActivity.PRIVACY_RULES_TYPE_ADDED_BY_PHONE;
import static org.telegram.ui.PrivacyControlActivity.PRIVACY_RULES_TYPE_BIO;
import static org.telegram.ui.PrivacyControlActivity.PRIVACY_RULES_TYPE_CALLS;
import static org.telegram.ui.PrivacyControlActivity.PRIVACY_RULES_TYPE_FORWARDS;
import static org.telegram.ui.PrivacyControlActivity.PRIVACY_RULES_TYPE_INVITE;
import static org.telegram.ui.PrivacyControlActivity.PRIVACY_RULES_TYPE_LASTSEEN;
import static org.telegram.ui.PrivacyControlActivity.PRIVACY_RULES_TYPE_P2P;
import static org.telegram.ui.PrivacyControlActivity.PRIVACY_RULES_TYPE_PHONE;
import static org.telegram.ui.PrivacyControlActivity.PRIVACY_RULES_TYPE_PHOTO;

import androidx.core.util.Consumer;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.util.concurrent.atomic.AtomicBoolean;

public class PrivacyChecker implements NotificationCenter.NotificationCenterDelegate {
    private final int accountNum;
    private final Consumer<Boolean> handler;
    private AtomicBoolean notificationDelegateLocked = new AtomicBoolean(false);

    private PrivacyChecker(int accountNum, Consumer<Boolean> handler) {
        this.accountNum = accountNum;
        this.handler = handler;
    }

    public static void fix(int account, Runnable onError, Runnable onSuccess) {
        setupPrivacySettings(account, PRIVACY_RULES_TYPE_PHONE, onError, () -> {
            setupPrivacySettings(account, PRIVACY_RULES_TYPE_FORWARDS, onError, () -> {
                setupPrivacySettings(account, PRIVACY_RULES_TYPE_PHOTO, onError, () -> {
                    setupPrivacySettings(account, PRIVACY_RULES_TYPE_P2P, onError, () -> {
                        setupPrivacySettings(account, PRIVACY_RULES_TYPE_CALLS, onError, () -> {
                            setupPrivacySettings(account, PRIVACY_RULES_TYPE_INVITE, onError, () -> {
                                setupPrivacySettings(account, PRIVACY_RULES_TYPE_LASTSEEN, onError, () -> {
                                    setupPrivacySettings(account, PRIVACY_RULES_TYPE_BIO, onError, onSuccess);
                                });
                            });
                        });
                    });
                });
            });
        });
    }

    private static void setupPrivacySettings(int account, int rulesType, Runnable onError, Runnable onSuccess) {
    }

    public static void check(int accountNum, Consumer<Boolean> handler) {
        PrivacyChecker checker = new PrivacyChecker(accountNum, handler);
        checker.check();
    }

    private void check() {
        AndroidUtilities.runOnUIThread(() -> {
            NotificationCenter.getInstance(accountNum).addObserver(this, NotificationCenter.privacyRulesUpdated);
            ContactsController.getInstance(accountNum).loadPrivacySettings();
        });
    }

    private boolean isGoodPrivacy() {
        ContactsController controller = ContactsController.getInstance(accountNum);
        if (controller.getPrivacyRules(PRIVACY_RULES_TYPE_PHONE).stream().anyMatch(r -> r instanceof TLRPC.TL_privacyValueAllowAll))
            return false;
        if (controller.getPrivacyRules(PRIVACY_RULES_TYPE_LASTSEEN).stream().anyMatch(r -> r instanceof TLRPC.TL_privacyValueAllowAll))
            return false;
        if (controller.getPrivacyRules(PRIVACY_RULES_TYPE_CALLS).stream().anyMatch(r -> r instanceof TLRPC.TL_privacyValueAllowAll))
            return false;
        return true;
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.privacyRulesUpdated && notificationDelegateLocked.compareAndSet(false, true)) {
            ContactsController controller = ContactsController.getInstance(accountNum);
            if (controller.getPrivacyRules(PRIVACY_RULES_TYPE_PHONE) == null
                    || controller.getPrivacyRules(PRIVACY_RULES_TYPE_LASTSEEN) == null
                    || controller.getPrivacyRules(PRIVACY_RULES_TYPE_CALLS) == null) {
                notificationDelegateLocked.set(false);
                return;
            }
            NotificationCenter.getInstance(accountNum).removeObserver(this, NotificationCenter.privacyRulesUpdated);
            handler.accept(isGoodPrivacy());
            notificationDelegateLocked.set(false);
        }
    }
}
