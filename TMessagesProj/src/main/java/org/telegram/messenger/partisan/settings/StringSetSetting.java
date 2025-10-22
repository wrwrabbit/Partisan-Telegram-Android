package org.telegram.messenger.partisan.settings;

import android.content.SharedPreferences;

import java.util.Set;

public class StringSetSetting  extends Setting<Set<String>> {
    public StringSetSetting(String key, Set<String> defaultValue) {
        super(key, defaultValue);
    }

    @Override
    protected void putValue(SharedPreferences.Editor editor, Set<String> newValue) {
        editor.putStringSet(key, newValue);
    }

    @Override
    public void load() {
        this.value = getLocalPreferences().getStringSet(key, defaultValue);
    }
}
