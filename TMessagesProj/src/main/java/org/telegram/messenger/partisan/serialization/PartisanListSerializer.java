package org.telegram.messenger.partisan.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;

import java.io.IOException;
import java.util.List;

public class PartisanListSerializer<T> extends JsonSerializer<List<T>> {

    @Override
    public void serialize(List<T> values, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStartArray();
        for (T value : values) {
            serializers.defaultSerializeValue(value, gen);
        }
        gen.writeEndArray();
    }

    @Override
    public void serializeWithType(List<T> values, JsonGenerator gen, SerializerProvider serializers, TypeSerializer typeSer) throws IOException {
        serialize(values, gen, serializers);
    }
}
