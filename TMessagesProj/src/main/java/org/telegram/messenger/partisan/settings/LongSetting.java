package org.telegram.messenger.partisan.settings;

import android.content.SharedPreferences;

public class LongSetting extends Setting<Long> {

    LongSetting(String key, Long defaultValue) {
        super(key, defaultValue);
    }

    @Override
    protected void putValue(SharedPreferences.Editor editor, Long newValue) {
        editor.putLong(key, newValue);
    }

    @Override
    public void load() {
        this.value = getLocalPreferences().getLong(key, defaultValue);
    }
}
