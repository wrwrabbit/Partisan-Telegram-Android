package org.telegram.messenger.partisan.settings;

import android.content.SharedPreferences;

public class FloatSetting extends Setting<Float> {

    FloatSetting(String key, Float defaultValue) {
        super(key, defaultValue);
    }

    @Override
    protected void putValue(SharedPreferences.Editor editor, Float newValue) {
        editor.putFloat(key, newValue);
    }

    @Override
    public void load() {
        this.value = getLocalPreferences().getFloat(key, defaultValue);
    }
}
