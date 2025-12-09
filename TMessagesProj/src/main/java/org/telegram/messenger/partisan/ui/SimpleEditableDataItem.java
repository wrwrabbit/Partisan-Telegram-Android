package org.telegram.messenger.partisan.ui;

import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.partisan.settings.StringSetting;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.EditTextCaption;
import org.telegram.ui.DialogBuilder.DialogTemplate;
import org.telegram.ui.DialogBuilder.DialogType;
import org.telegram.ui.DialogBuilder.FakePasscodeDialogBuilder;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class SimpleEditableDataItem extends AbstractItem {
    private final String text;
    private final Supplier<String> getValue;
    private final Consumer<String> setValue;
    private final Supplier<String> getCellValue;
    private boolean multiline = false;

    public SimpleEditableDataItem(BaseFragment fragment, String text, StringSetting setting) {
        this(fragment, text, () -> setting.get().get(), setting::set);
    }

    public SimpleEditableDataItem(BaseFragment fragment, String text, Supplier<String> getValue, Consumer<String> setValue) {
        this(fragment, text, getValue, setValue, getValue);
    }

    public SimpleEditableDataItem(BaseFragment fragment, String text, Supplier<String> getValue, Consumer<String> setValue, Supplier<String> getCellValue) {
        super(fragment, ItemType.BUTTON.ordinal());
        this.text = text;
        this.getValue = getValue;
        this.setValue = setValue;
        this.getCellValue = getCellValue;
    }

    public SimpleEditableDataItem setMultiline() {
        this.multiline = true;
        return this;
    }

    @Override
    public void onBindViewHolderInternal(RecyclerView.ViewHolder holder, int position) {
        ((TextSettingsCell) holder.itemView).setTextAndValue(text, getCellValue.get(), true);
    }

    @Override
    public void onClick(View view) {
        DialogTemplate template = new DialogTemplate();
        template.type = DialogType.EDIT;
        template.title = text;
        String value = getValue.get();
        template.addEditTemplate(value, text, !multiline);
        TextSettingsCell cell = (TextSettingsCell) view;
        template.positiveListener = views -> {
            setValue.accept(((EditTextCaption)views.get(0)).getText().toString());
            cell.setTextAndValue(text, getCellValue.get(), true);
        };
        template.negativeListener = (dlg, whichButton) -> {
            setValue.accept("");
            cell.setTextAndValue(text, getCellValue.get(), true);
        };
        AlertDialog dialog = FakePasscodeDialogBuilder.build(fragment.getParentActivity(), template);
        fragment.showDialog(dialog);
    }

    @Override
    public boolean isEnabledInternal() {
        return true;
    }
}
