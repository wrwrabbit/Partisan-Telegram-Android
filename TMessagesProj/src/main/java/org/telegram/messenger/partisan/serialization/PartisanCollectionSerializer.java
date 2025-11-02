package org.telegram.messenger.partisan.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;

import java.io.IOException;
import java.util.Collection;

public class PartisanCollectionSerializer<T> extends JsonSerializer<Collection<T>> {

    @Override
    public void serialize(Collection<T> values, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStartArray();
        for (T value : values) {
            serializers.defaultSerializeValue(value, gen);
        }
        gen.writeEndArray();
    }

    @Override
    public void serializeWithType(Collection<T> values, JsonGenerator gen, SerializerProvider serializers, TypeSerializer typeSer) throws IOException {
        serialize(values, gen, serializers);
    }
}
