package org.telegram.messenger.partisan.settings;

import android.content.SharedPreferences;

public class EnumSetting<T extends Enum<T>> extends Setting<T> {
    private final Class<T> clazz;

    public EnumSetting(String key, T defaultValue, Class<T> clazz) {
        super(key, defaultValue);
        this.clazz = clazz;
    }

    @Override
    protected void putValue(SharedPreferences.Editor editor, T newValue) {
        editor.putString(key, newValue.name());
    }

    @Override
    public void load() {
        String valueName = getLocalPreferences().getString(key, defaultValue.name());
        this.value = Enum.valueOf(clazz, valueName);
    }
}
