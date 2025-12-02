package org.telegram.messenger.partisan.settings;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

public class SettingUtils {
    public static List<Setting<?>> getAllSettings(Class<?> settingsClass) {
        List<Setting<?>> settings = new ArrayList<>();
        for (Field field : settingsClass.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && Setting.class.isAssignableFrom(field.getType())) {
                try {
                    settings.add((Setting<?>) field.get(settingsClass));
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return settings;
    }
}
