package org.telegram.messenger.partisan.serialization;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;
import com.fasterxml.jackson.databind.jsontype.TypeDeserializer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PartisanListDeserializer<T> extends JsonDeserializer<List<T>> implements ContextualDeserializer {

    private static class TypeDefinitionSkipper {
        private final JsonParser parser;
        private boolean checkedArrayTypeStringPresence;
        private boolean containsTypeString;

        public TypeDefinitionSkipper(JsonParser parser) {
            this.parser = parser;
        }

        public boolean skipTypeDefinitionIfNeeded() throws IOException {
            if (checkedArrayTypeStringPresence) {
                return false;
            }
            checkedArrayTypeStringPresence = true;
            if (isArrayTypeString()) {
                containsTypeString = true;
                parser.nextToken(); // Skip type definition
                return true;
            }
            return false;
        }

        private boolean isArrayTypeString() throws IOException {
            if (parser.currentToken() != JsonToken.VALUE_STRING) {
                return false;
            }
            return "java.util.Collections$SynchronizedRandomAccessList".equals(parser.getValueAsString())
                    || "java.util.ArrayList".equals(parser.getValueAsString());
        }

        public void skipEndArrayIfNeeded() throws IOException {
            if (containsTypeString) {
                parser.nextToken(); // Skip JsonToken.END_ARRAY
            }
        }
    }

    private final Class<T> valueClass;

    public PartisanListDeserializer() {
        this.valueClass = null;
    }

    public PartisanListDeserializer(Class<T> valueClass) {
        this.valueClass = valueClass;
    }

    public JsonDeserializer<?> createContextual(DeserializationContext ctxt,
                                                BeanProperty property) {
        return new PartisanListDeserializer<>(property.getType().getContentType().getRawClass());
    }

    @Override
    public List<T> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        List<T> valueList = new ArrayList<>();

        TypeDefinitionSkipper typeDefinitionSkipper = new TypeDefinitionSkipper(p);

        if (p.currentToken() == JsonToken.START_ARRAY) {
            while (p.nextToken() != JsonToken.END_ARRAY) {
                if (typeDefinitionSkipper.skipTypeDefinitionIfNeeded()) {
                    continue;
                }

                T value = p.readValueAs(valueClass);
                valueList.add(value);
            }
            typeDefinitionSkipper.skipEndArrayIfNeeded();
        }
        return Collections.synchronizedList(valueList);
    }

    @Override
    public Object deserializeWithType(JsonParser p, DeserializationContext ctxt, TypeDeserializer typeDeserializer) throws IOException {
        return deserialize(p, ctxt);
    }
}
