package org.telegram.ui.DialogBuilder;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;

import java.util.ArrayList;
import java.util.List;

public class FakePasscodeDialogBuilder {
    public static AlertDialog build(Context context, DialogTemplate template) {
        return buildAndGetViews(context, template, new ArrayList<>());
    }

    public static AlertDialog buildAndGetViews(Context context, DialogTemplate template, List<View> viewsOutput) {
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(context);
        dialogBuilder.setTitle(template.title);
        if (template.message != null) {
            dialogBuilder.setMessage(template.message);
        }

        for (ViewTemplate viewTemplate: template.viewTemplates) {
            viewsOutput.add(viewTemplate.create(context));
        }
        LinearLayout layout = createLayout(dialogBuilder.getContext(), viewsOutput);
        dialogBuilder.setView(layout);

        if (template.type == DialogType.ADD) {
            dialogBuilder.setPositiveButton(LocaleController.getString(R.string.Add), null);
            dialogBuilder.setNeutralButton(LocaleController.getString(R.string.Cancel), null);
        } if (template.type == DialogType.CREATE) {
            dialogBuilder.setPositiveButton(LocaleController.getString(R.string.Create), null);
            dialogBuilder.setNeutralButton(LocaleController.getString(R.string.Cancel), null);
        } else if (template.type == DialogType.DELETE) {
            dialogBuilder.setPositiveButton(LocaleController.getString(R.string.Delete), null);
            dialogBuilder.setNeutralButton(LocaleController.getString(R.string.Cancel), null);
        } else if (template.type == DialogType.EDIT) {
            dialogBuilder.setPositiveButton(LocaleController.getString(R.string.Save), null);
            dialogBuilder.setNeutralButton(LocaleController.getString(R.string.Cancel), null);
            dialogBuilder.setNegativeButton(LocaleController.getString(R.string.Delete), template.negativeListener);
        } else if (template.type == DialogType.ONLY_SAVE) {
            dialogBuilder.setPositiveButton(LocaleController.getString(R.string.Save), null);
            dialogBuilder.setNeutralButton(LocaleController.getString(R.string.Cancel), null);
        } else if (template.type == DialogType.OK) {
            dialogBuilder.setPositiveButton(LocaleController.getString(R.string.OK), null);
        }
        AlertDialog dialog = dialogBuilder.create();
        addPositiveButtonListener(dialog, template, viewsOutput);
        return dialog;
    }

    private static LinearLayout createLayout(Context context, List<View> views) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(30, 0, 30, 0);
        for (View view: views) {
            layout.addView(view, lp);
        }
        return layout;
    }

    private static void addPositiveButtonListener(AlertDialog dialog, DialogTemplate template, List<View> views) {
        dialog.setOnShowListener(dialogInterface -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                boolean error = false;
                for (int i = 0; i < views.size(); i++) {
                    if (!template.viewTemplates.get(i).validate(views.get(i))) {
                        error = true;
                    }
                }
                if (!error) {
                    template.positiveListener.accept(views);
                    dialog.dismiss();
                }
            });
        });
    }
}