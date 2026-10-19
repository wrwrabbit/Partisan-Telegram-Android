package org.telegram.ui.DialogBuilder;

import android.content.DialogInterface;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public class DialogButtonWithTimer {
    private static class Info {
        String text;
        public int timeout;
        public boolean isDialogDismissed = false;
    }

    private static class TimedButton {
        final int buttonType;
        final Info info;

        TimedButton(int buttonType, Info info) {
            this.buttonType = buttonType;
            this.info = info;
        }
    }

    // A dialog keeps only one show listener, so every timed button on it has to be started from a
    // single shared one instead of each call installing its own.
    private static final Map<AlertDialog, List<TimedButton>> timedButtons = new WeakHashMap<>();

    public static void setButton(AlertDialog dialog, int buttonType, String text, int timeout, final DialogInterface.OnClickListener listener) {
        Info info = new Info();
        info.text = text;
        info.timeout = timeout;

        dialog.setButton(buttonType, text, (dlg, which) -> {
            if (info.timeout == 0) {
                listener.onClick(dlg, which);
            }
        });

        List<TimedButton> buttons = timedButtons.get(dialog);
        if (buttons == null) {
            buttons = new ArrayList<>();
            timedButtons.put(dialog, buttons);
        }
        buttons.add(new TimedButton(buttonType, info));
        List<TimedButton> dialogButtons = buttons;
        dialog.setOnShowListener(dlg -> {
            for (TimedButton timedButton : dialogButtons) {
                startCountdown(dialog, timedButton);
            }
        });
    }

    private static void startCountdown(AlertDialog dialog, TimedButton timedButton) {
        Info info = timedButton.info;
        TextView button = (TextView) dialog.getButton(timedButton.buttonType);
        info.text = button.getText().toString();
        button.setText(info.text + " (" + info.timeout + ")");
        button.setTextColor(Theme.getColor(Theme.key_dialogTextGray3));
        button.setEnabled(false);
        TimeoutRunnable timeoutRunnable = new TimeoutRunnable(button, info);
        Utilities.globalQueue.postRunnable(timeoutRunnable, 1000);
    }

    private static class TimeoutRunnable implements Runnable {
        TextView cancelButton;
        public Info info;

        public TimeoutRunnable(TextView cancelButton, Info info) {
            this.cancelButton = cancelButton;
            this.info = info;
        }

        @Override
        public void run() {
            if (!info.isDialogDismissed) {
                info.timeout--;
                AndroidUtilities.runOnUIThread(() -> {
                    if (info.timeout > 0) {
                        cancelButton.setText(info.text + " (" + info.timeout + ")");
                    } else {
                        cancelButton.setText(info.text);
                        cancelButton.setTextColor(Theme.getColor(Theme.key_dialogButton));
                        cancelButton.setEnabled(true);
                    }
                });
                if (info.timeout > 0) {
                    Utilities.globalQueue.postRunnable(this, 1000);
                }
            }
        }
    }
}
