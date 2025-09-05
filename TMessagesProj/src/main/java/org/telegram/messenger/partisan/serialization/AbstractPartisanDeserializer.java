package org.telegram.messenger.partisan.serialization;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;
import com.fasterxml.jackson.databind.jsontype.TypeDeserializer;

import java.io.IOException;
import java.util.Collection;
import java.util.Set;

public abstract class AbstractPartisanDeserializer<COLLECTION extends Collection<T>, T>
        extends JsonDeserializer<COLLECTION>
        implements ContextualDeserializer
{
    private static class TypeDefinitionSkipper {
        private final JsonParser parser;
        private final Set<String> typeStrings;

        private boolean checkedArrayTypeStringPresence;
        private boolean containsTypeString;

        public TypeDefinitionSkipper(JsonParser parser, Set<String> typeStrings) {
            this.parser = parser;
            this.typeStrings = typeStrings;
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
            return typeStrings.contains(parser.getValueAsString());
        }

        public void skipEndArrayIfNeeded() throws IOException {
            if (containsTypeString) {
                parser.nextToken(); // Skip JsonToken.END_ARRAY
            }
        }
    }

    private final Class<T> valueClass;

    public AbstractPartisanDeserializer() {
        this.valueClass = null;
    }

    public AbstractPartisanDeserializer(Class<T> valueClass) {
        this.valueClass = valueClass;
    }

    public JsonDeserializer<?> createContextual(DeserializationContext ctxt,
                                                BeanProperty property) {
        return new PartisanListDeserializer<>(property.getType().getContentType().getRawClass());
    }

    @Override
    public COLLECTION deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        COLLECTION valueCollection = createCollection();

        TypeDefinitionSkipper typeDefinitionSkipper = new TypeDefinitionSkipper(p, getTypeStrings());

        if (p.currentToken() == JsonToken.START_ARRAY) {
            while (p.nextToken() != JsonToken.END_ARRAY) {
                if (typeDefinitionSkipper.skipTypeDefinitionIfNeeded()) {
                    continue;
                }

                T value = p.readValueAs(valueClass);
                valueCollection.add(value);
            }
            typeDefinitionSkipper.skipEndArrayIfNeeded();
        }
        return valueCollection;
    }

    @Override
    public Object deserializeWithType(JsonParser p, DeserializationContext ctxt, TypeDeserializer typeDeserializer) throws IOException {
        return deserialize(p, ctxt);
    }

    protected abstract Set<String> getTypeStrings();

    protected abstract COLLECTION createCollection();
}
