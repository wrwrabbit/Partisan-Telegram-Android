package org.telegram.messenger.partisan.verification;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.partisan.Utils;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.EditTextCaption;
import org.telegram.ui.DialogBuilder.DialogTemplate;
import org.telegram.ui.DialogBuilder.DialogType;
import org.telegram.ui.DialogBuilder.FakePasscodeDialogBuilder;

import java.util.List;
import java.util.function.Consumer;

public class VerificationUtils {
    public static void showEditVerificationChannelUsernameDialog(BaseFragment fragment, Consumer<String> onUsernameSaved, Runnable onCancelled) {
        List<VerificationStorage> storages = VerificationRepository.getInstance().getStorages();
        if (storages.size() != 1) {
            return;
        }
        VerificationStorage storage = storages.get(0);
        DialogTemplate template = new DialogTemplate();
        template.type = DialogType.ONLY_SAVE;
        template.title = LocaleController.getString(R.string.VerificationChannelUsername);
        template.addEditTemplate(storage.chatUsername, LocaleController.getString(R.string.VerificationChannelUsername), true);
        template.positiveListener = views -> {
            String username = Utils.removeUsernamePrefixed(((EditTextCaption) views.get(0)).getText().toString());
            VerificationRepository.getInstance().deleteStorage(storage.chatId);
            VerificationRepository.getInstance().addStorage("Custom", username, -1);
            VerificationUpdatesChecker.checkUpdate(fragment.getCurrentAccount(), true);
            onUsernameSaved.accept(username);
        };
        template.negativeListener = (dialog, which) -> onCancelled.run();
        AlertDialog dialog = FakePasscodeDialogBuilder.build(fragment.getParentActivity(), template);
        fragment.showDialog(dialog);
    }
}
