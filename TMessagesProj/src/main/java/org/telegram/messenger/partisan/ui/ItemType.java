package org.telegram.messenger.partisan.ui;

import android.content.Context;
import android.view.View;

import org.telegram.messenger.partisan.voicechange.RecordItem;

import java.util.function.Function;

public enum ItemType {
    TOGGLE(ToggleItem::createView),
    RADIO_BUTTON(RadioButtonItem::createView),
    BUTTON(ButtonItem::createView),
    BUTTON_WITH_ICON(ButtonWithIconItem::createView),
    SEEK_BAR(SeekBarItem::createView),
    VALUES_SLIDER(ValuesSlideChooseItem::createView),
    HEADER(HeaderItem::createView),
    DESCRIPTION(DescriptionItem::createView),
    DELIMITER(DelimiterItem::createView),
    RECORD(RecordItem::createView);

    private final Function<Context, View> viewConstructor;

    ItemType(Function<Context, View> viewConstructor) {
        this.viewConstructor = viewConstructor;
    }

    public View createView(Context context) {
        return viewConstructor.apply(context);
    }
}
