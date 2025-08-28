package org.telegram.messenger.partisan.settings;

import android.content.SharedPreferences;

public class BooleanSetting extends Setting<Boolean> {

    BooleanSetting(String key, Boolean defaultValue) {
        super(key, defaultValue);
    }

    @Override
    protected void putValue(SharedPreferences.Editor editor, Boolean newValue) {
        editor.putBoolean(key, newValue);
    }

    @Override
    public void load() {
        this.value = getLocalPreferences().getBoolean(key, defaultValue);
    }
}
