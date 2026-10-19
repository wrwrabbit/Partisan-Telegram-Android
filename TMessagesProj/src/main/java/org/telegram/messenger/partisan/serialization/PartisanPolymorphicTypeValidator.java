package org.telegram.messenger.partisan.serialization;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;

import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLObject;

import java.util.Collection;
import java.util.Map;

/**
 * Allow-list for type ids read by Jackson default typing. Names outside java.util and
 * org.telegram are denied before their class is loaded, so they can't trigger static initializers.
 * Generic ids are rejected separately by {@link PartisanDefaultTyping}.
 */
public class PartisanPolymorphicTypeValidator extends PolymorphicTypeValidator.Base {
    private static final long serialVersionUID = 1L;

    public static final PartisanPolymorphicTypeValidator INSTANCE = new PartisanPolymorphicTypeValidator();

    private static final String[] ALLOWED_APP_PREFIXES = {
            "org.telegram.messenger.fakepasscode.",
            "org.telegram.messenger.partisan.",
            "org.telegram.tgnet.",
    };
    private static final String CHAT_INFO_OVERRIDE_CLASS_NAME = UserConfig.ChatInfoOverride.class.getName();
    private static final String JAVA_UTIL_PREFIX = "java.util.";
    private static final String TELEGRAM_PREFIX = "org.telegram.";

    private PartisanPolymorphicTypeValidator() {
    }

    @Override
    public Validity validateSubClassName(MapperConfig<?> config, JavaType baseType, String subClassName) {
        if (isAllowedAppClassName(subClassName)) {
            return Validity.ALLOWED;
        }
        if (subClassName.startsWith(JAVA_UTIL_PREFIX) || subClassName.startsWith(TELEGRAM_PREFIX)) {
            return Validity.INDETERMINATE; // decided in validateSubType once the class is known
        }
        return Validity.DENIED;
    }

    @Override
    public Validity validateSubType(MapperConfig<?> config, JavaType baseType, JavaType subType) {
        Class<?> rawClass = subType.getRawClass();
        String className = rawClass.getName();
        if (className.startsWith(JAVA_UTIL_PREFIX)
                && (Collection.class.isAssignableFrom(rawClass) || Map.class.isAssignableFrom(rawClass))) {
            return Validity.ALLOWED;
        }
        // TL subclasses outside tgnet, e.g. MessageMediaStoryFull created by MessageMedia.TLdeserialize
        if (className.startsWith(TELEGRAM_PREFIX) && TLObject.class.isAssignableFrom(rawClass)) {
            return Validity.ALLOWED;
        }
        return Validity.DENIED;
    }

    private static boolean isAllowedAppClassName(String className) {
        if (className.equals(CHAT_INFO_OVERRIDE_CLASS_NAME)) {
            return true;
        }
        for (String prefix : ALLOWED_APP_PREFIXES) {
            if (className.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
