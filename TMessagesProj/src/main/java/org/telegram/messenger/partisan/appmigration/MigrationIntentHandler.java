package org.telegram.messenger.partisan.appmigration;

import android.content.Intent;
import android.text.TextUtils;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.partisan.PartisanLog;
import org.telegram.messenger.partisan.appmigration.intenthandlers.AbstractIntentHandler;
import org.telegram.messenger.partisan.appmigration.intenthandlers.MigrationResultHandler;
import org.telegram.messenger.partisan.appmigration.intenthandlers.SignatureConfirmationHandler;
import org.telegram.messenger.partisan.appmigration.intenthandlers.ZipHandler;
import org.telegram.messenger.partisan.appmigration.intenthandlers.ZipRequestHandler;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.LaunchActivity;

import java.util.List;

public class MigrationIntentHandler {
    private static final List<AbstractIntentHandler> handlers = List.of(
            new SignatureConfirmationHandler(),
            new ZipRequestHandler(),
            new ZipHandler(),
            new MigrationResultHandler()
    );

    public static synchronized void checkOtherPtgIntent(LaunchActivity activity, int account) {
        Intent intent = activity.getIntent();
        if (!intent.getBooleanExtra("fromOtherPtg", false)) {
            return;
        }

        for (AbstractIntentHandler handler : handlers) {
            if (handler.needHandleIntent(intent, activity)) {
                PartisanLog.d("MigrationZipReceiver: handle " + handler.getClass());
                handler.handleIntent(intent, activity);

                BaseFragment fragmentToPresent = handler.getFragmentToPresent(intent);
                if (fragmentToPresent != null) {
                    applyLanguageFromIntent(intent, account);
                    activity.presentFragment(fragmentToPresent);
                }
                break;
            }
        }
    }

    private static void applyLanguageFromIntent(Intent intent, int account) {
        if (intent.hasExtra("language")) {
            String targetLanguage = intent.getStringExtra("language");
            LocaleController.LocaleInfo localeInfo = LocaleController.getInstance().languages.stream()
                    .filter(l -> TextUtils.equals(l.shortName, targetLanguage))
                    .findFirst()
                    .orElse(null);
            if (localeInfo != null) {
                LocaleController.getInstance().applyLanguage(localeInfo, true, false, false, true, account, null);
            }
        }
    }
}
