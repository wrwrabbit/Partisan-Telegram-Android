package org.telegram.messenger.partisan.settings;

import android.content.SharedPreferences;

public class StringSetting extends Setting<String> {
    StringSetting(String key, String defaultValue) {
        super(key, defaultValue);
    }

    @Override
    protected void putValue(SharedPreferences.Editor editor, String newValue) {
        editor.putString(key, newValue);
    }

    @Override
    public void load() {
        this.value = getLocalPreferences().getString(key, defaultValue);
    }
}
