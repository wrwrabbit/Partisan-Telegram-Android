package org.telegram.messenger.partisan.serialization;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class PartisanSetDeserializer<T> extends AbstractPartisanDeserializer<Set<T>, T> {
    public PartisanSetDeserializer() {
    }

    public PartisanSetDeserializer(Class<T> valueClass) {
        super(valueClass);
    }

    @Override
    protected Set<String> getTypeStrings() {
        return Set.of("java.util.Collections$SynchronizedSet", "java.util.HashSet");
    }

    @Override
    protected Set<T> createCollection() {
        return Collections.synchronizedSet(new HashSet<>());
    }
}
