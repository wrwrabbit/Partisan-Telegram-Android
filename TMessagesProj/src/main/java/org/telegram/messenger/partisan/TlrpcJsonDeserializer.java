package org.telegram.messenger.partisan;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.jsontype.TypeDeserializer;

import org.telegram.messenger.Utilities;
import org.telegram.tgnet.InputSerializedData;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLObject;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class TlrpcJsonDeserializer extends StdDeserializer<TLObject> {
    public TlrpcJsonDeserializer(Class<?> vc) {
        super(vc);
    }

    @Override
    public TLObject deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
        try {
            return deserializeObject(p.getValueAsString(), handledType());
        } catch (Exception e) {
            throw new IOException("TLObject deserialization failed", e);
        }
    }

    protected String _locateTypeId(JsonParser p, DeserializationContext ctxt) throws IOException {
        if (!p.isExpectedStartArrayToken()) {
            ctxt.reportWrongTokenException(TLObject.class, JsonToken.START_ARRAY,
                    "need JSON Array to contain As.WRAPPER_ARRAY type information for class "+TLObject.class.getName());
            return null;
        }

        JsonToken t = p.nextToken();
        if (t == JsonToken.VALUE_STRING) {
            String result = p.getText();
            p.nextToken();
            return result;
        }

        ctxt.reportWrongTokenException(TLObject.class, JsonToken.VALUE_STRING,
                "need JSON String that contains type id (for subtype of %s)", TLObject.class.getName());
        return null;
    }

    @Override
    public Object deserializeWithType(JsonParser p, DeserializationContext ctxt, TypeDeserializer typeDeserializer) throws IOException {
        try {
            boolean hadStartArray = p.isExpectedStartArrayToken();
            String typeId = _locateTypeId(p, ctxt);

            if (hadStartArray && p.currentToken() == JsonToken.END_ARRAY) {
                return getNullValue(ctxt);
            }
            Object obj = deserializeObject(p.getValueAsString(), Class.forName(typeId));

            if (hadStartArray && p.nextToken() != JsonToken.END_ARRAY) {
                ctxt.reportWrongTokenException(TLObject.class, JsonToken.END_ARRAY,
                        "expected closing END_ARRAY after type information and deserialized value");
                return null;
            }
            return obj;
        } catch (Exception e) {
            throw new IOException("TLObject deserialization failed", e);
        }
    }

    private TLObject deserializeObject(String value, Class<?> clazz) throws IOException, InvocationTargetException, IllegalAccessException {

        SerializedData data = new SerializedData(Utilities.hexToBytes(value));
        Object obj = null;
        do {
            Method method;
            try {
                method = clazz.getMethod("TLdeserialize", InputSerializedData.class, int.class, boolean.class);
            } catch (Exception ignore) {
                method = null;
            }
            if (method != null) {
                obj = method.invoke(null, data, data.readInt32(false), false);
                break;
            }
            clazz = clazz.getSuperclass();
        } while (clazz != null);
        if (!(obj instanceof TLObject)) {
            throw new IOException("TLdeserialize was not found");
        }
        return (TLObject) obj;
    }
}
