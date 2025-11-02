package org.telegram.messenger.partisan.serialization;

import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class PartisanListDeserializer<T> extends AbstractPartisanDeserializer<List<T>, T> {
    public PartisanListDeserializer() {
    }

    public PartisanListDeserializer(Class<T> valueClass) {
        super(valueClass);
    }

    public JsonDeserializer<?> createContextual(DeserializationContext ctxt, BeanProperty property) {
        return new PartisanListDeserializer<>(property.getType().getContentType().getRawClass());
    }

    @Override
    protected Set<String> getTypeStrings() {
        return Set.of("java.util.Collections$SynchronizedRandomAccessList", "java.util.ArrayList");
    }

    @Override
    protected List<T> createCollection() {
        return Collections.synchronizedList(new ArrayList<>());
    }
}
