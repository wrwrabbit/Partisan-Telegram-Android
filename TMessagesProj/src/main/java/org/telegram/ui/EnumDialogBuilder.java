package org.telegram.ui;

import android.content.Context;
import android.widget.LinearLayout;

import androidx.core.util.Consumer;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.RadioColorCell;

public class EnumDialogBuilder {
    public static AlertDialog build(Context context, String title, int selected, String[] items, Consumer<Integer> onClick) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(title);
        final LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        builder.setView(linearLayout);

        for (int a = 0; a < items.length; a++) {
            RadioColorCell cell = new RadioColorCell(context);
            cell.setPadding(AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4), 0);
            cell.setTag(a);
            cell.setCheckColor(Theme.getColor(Theme.key_radioBackground), Theme.getColor(Theme.key_dialogRadioBackgroundChecked));
            cell.setTextAndValue(items[a], selected == a);
            linearLayout.addView(cell);
            cell.setOnClickListener(v -> {
                builder.getDismissRunnable().run();
                Integer which = (Integer) v.getTag();
                onClick.accept(which);
            });
        }
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        return builder.create();
    }
}
