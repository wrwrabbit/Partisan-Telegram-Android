package org.telegram.messenger.partisan.serialization;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DatabindContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.TypeIdResolver;
import com.fasterxml.jackson.databind.jsontype.TypeResolverBuilder;
import com.fasterxml.jackson.databind.jsontype.impl.ClassNameIdResolver;
import com.fasterxml.jackson.databind.type.TypeFactory;

import java.io.IOException;
import java.util.Collection;

public class PartisanDefaultTyping {
    private PartisanDefaultTyping() {
    }

    public static void activate(ObjectMapper mapper) {
        TypeResolverBuilder<?> typer = new NonGenericTypeResolverBuilder()
                .init(JsonTypeInfo.Id.CLASS, null)
                .inclusion(JsonTypeInfo.As.WRAPPER_ARRAY);
        mapper.setDefaultTyping(typer);
    }

    public static void requireNonGenericTypeId(String typeId) throws IOException {
        if (typeId.indexOf('<') >= 0) {
            throw new IOException("Generic type id is not allowed: " + typeId);
        }
    }

    private static class NonGenericTypeResolverBuilder extends ObjectMapper.DefaultTypeResolverBuilder {
        private static final long serialVersionUID = 1L;

        NonGenericTypeResolverBuilder() {
            super(ObjectMapper.DefaultTyping.OBJECT_AND_NON_CONCRETE, PartisanPolymorphicTypeValidator.INSTANCE);
        }

        @Override
        protected TypeIdResolver idResolver(MapperConfig<?> config, JavaType baseType, PolymorphicTypeValidator subtypeValidator,
                                            Collection<NamedType> subtypes, boolean forSer, boolean forDeser) {
            return new NonGenericClassNameIdResolver(baseType, config.getTypeFactory(), subtypeValidator);
        }
    }

    private static class NonGenericClassNameIdResolver extends ClassNameIdResolver {
        NonGenericClassNameIdResolver(JavaType baseType, TypeFactory typeFactory, PolymorphicTypeValidator subtypeValidator) {
            super(baseType, typeFactory, subtypeValidator);
        }

        @Override
        protected JavaType _typeFromId(String id, DatabindContext ctxt) throws IOException {
            requireNonGenericTypeId(id);
            return super._typeFromId(id, ctxt);
        }
    }
}
