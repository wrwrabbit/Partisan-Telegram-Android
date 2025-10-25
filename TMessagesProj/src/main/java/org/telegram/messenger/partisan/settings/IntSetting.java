package org.telegram.messenger.partisan.settings;

import android.content.SharedPreferences;

public class IntSetting extends Setting<Integer> {

    public IntSetting(String key, Integer defaultValue) {
        super(key, defaultValue);
    }

    @Override
    protected void putValue(SharedPreferences.Editor editor, Integer newValue) {
        editor.putFloat(key, newValue);
    }

    @Override
    public void load() {
        this.value = getLocalPreferences().getInt(key, defaultValue);
    }
}
