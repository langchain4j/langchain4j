package dev.langchain4j.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.exc.InvalidTypeIdException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.databind.type.TypeFactory;
import dev.langchain4j.Internal;
import dev.langchain4j.data.message.JacksonChatMessageJsonCodec;
import dev.langchain4j.exception.JsonTypeNotAllowedException;
import java.lang.reflect.Type;

/**
 * The Jackson 2 implementation, used when no {@code PolymorphicJsonCodecFactory} is registered.
 */
@Internal
class JacksonPolymorphicJsonCodec implements Json.JsonCodec {

    private final ObjectMapper mapper;

    JacksonPolymorphicJsonCodec(TypeAllowlist allowlist, ClassLoader classLoader) {
        // Built on the chat-message mapper because the values being written can include chat
        // messages, which need the same handling here as anywhere else.
        JsonMapper.Builder builder = JacksonChatMessageJsonCodec.chatMessageJsonMapperBuilder();
        if (classLoader != null) {
            builder.typeFactory(TypeFactory.defaultInstance().withClassLoader(classLoader));
        }
        this.mapper = builder.build();
        this.mapper.activateDefaultTyping(new AllowlistTypeValidator(allowlist));
    }

    @Override
    public String toJson(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public <T> T fromJson(String json, Class<T> type) {
        return fromJson(json, (Type) type);
    }

    @Override
    public <T> T fromJson(String json, Type type) {
        try {
            return mapper.readValue(json, mapper.constructType(type));
        } catch (InvalidTypeIdException e) {
            throw new JsonTypeNotAllowedException(e.getTypeId(), e);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private static final class AllowlistTypeValidator extends PolymorphicTypeValidator.Base {

        private static final long serialVersionUID = 1L;

        private final TypeAllowlist allowlist;

        private AllowlistTypeValidator(TypeAllowlist allowlist) {
            this.allowlist = allowlist;
        }

        @Override
        public Validity validateBaseType(MapperConfig<?> config, JavaType baseType) {
            return Validity.INDETERMINATE;
        }

        @Override
        public Validity validateSubClassName(MapperConfig<?> config, JavaType baseType, String subClassName) {
            return allowlist.isAllowedTypeId(subClassName) ? Validity.ALLOWED : Validity.INDETERMINATE;
        }

        @Override
        public Validity validateSubType(MapperConfig<?> config, JavaType baseType, JavaType subType) {
            return allowlist.isAlwaysAllowedType(subType.getRawClass()) ? Validity.ALLOWED : Validity.DENIED;
        }
    }
}
