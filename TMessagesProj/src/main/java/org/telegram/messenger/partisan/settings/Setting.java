package org.telegram.messenger.partisan.settings;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

public abstract class Setting<T> {
    public T value;
    protected final String key;
    protected final T defaultValue;
    private Supplier<Boolean> conditionForGet;

    Setting(String key, T defaultValue) {
        this.key = key;
        this.defaultValue = defaultValue;
        this.value = defaultValue;
    }

    public Optional<T> get() {
        if (conditionForGet != null && conditionForGet.get() == false) {
            return Optional.empty();
        } else {
            return Optional.of(value);
        }
    }

    public void set(T newValue) {
        this.value = newValue;
        SharedPreferences.Editor editor;
        editor = getLocalPreferences().edit();
        if (newValue != null && !Objects.equals(defaultValue, newValue)) {
            putValue(editor, newValue);
        } else {
            editor.remove(key);
        }
        editor.commit();
    }

    protected abstract void putValue(SharedPreferences.Editor editor, T newValue);

    public abstract void load();

    public void setConditionForGet(Supplier<Boolean> conditionForGet) {
        this.conditionForGet = conditionForGet;
    }

    protected static SharedPreferences getLocalPreferences() {
        return ApplicationLoader.applicationContext.getSharedPreferences("userconfing", Context.MODE_PRIVATE);
    }
}
