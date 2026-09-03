package dev.langchain4j.jackson3;

import dev.langchain4j.exception.JsonReadException;
import dev.langchain4j.exception.JsonTypeNotAllowedException;
import dev.langchain4j.exception.JsonWriteException;
import dev.langchain4j.internal.Json;
import dev.langchain4j.internal.TypeAllowlist;
import java.lang.reflect.Type;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DatabindContext;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.exc.InvalidTypeIdException;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.PolymorphicTypeValidator;

/**
 * Jackson 3 codec for JSON that carries type information.
 *
 * <p>Writes the same document as its Jackson 2 counterpart, which matters more here than elsewhere:
 * agent state persisted by one has to be readable by the other.
 */
public class Jackson3StateJsonCodec implements Json.JsonCodec {

    private final ObjectMapper objectMapper;

    public Jackson3StateJsonCodec(TypeAllowlist allowlist, ClassLoader classLoader) {
        // Built on the chat-message mapper because the values being written can include chat
        // messages, which need the same handling here as anywhere else.
        JsonMapper.Builder builder = Jackson3ChatMessageJsonCodec.chatMessageJsonMapperBuilder()
                .activateDefaultTyping(new AllowlistTypeValidator(allowlist));
        if (classLoader != null) {
            builder.typeFactory(builder.typeFactory().withClassLoader(classLoader));
        }
        this.objectMapper = builder.build();
    }

    @Override
    public String toJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (JacksonException e) {
            throw new JsonWriteException(e);
        }
    }

    @Override
    public <T> T fromJson(String json, Class<T> type) {
        return fromJson(json, (Type) type);
    }

    @Override
    public <T> T fromJson(String json, Type type) {
        try {
            return objectMapper.readValue(json, objectMapper.constructType(type));
        } catch (InvalidTypeIdException e) {
            throw new JsonTypeNotAllowedException(e.getTypeId(), e);
        } catch (JacksonException e) {
            throw new JsonReadException(e);
        }
    }

    /** Adapts {@link TypeAllowlist} to Jackson 3, as its Jackson 2 twin does. */
    private static final class AllowlistTypeValidator extends PolymorphicTypeValidator {

        private final TypeAllowlist allowlist;

        private AllowlistTypeValidator(TypeAllowlist allowlist) {
            this.allowlist = allowlist;
        }

        @Override
        public Validity validateBaseType(DatabindContext context, JavaType baseType) {
            return Validity.INDETERMINATE;
        }

        @Override
        public Validity validateSubClassName(DatabindContext context, JavaType baseType, String subClassName) {
            return allowlist.isAllowedTypeId(subClassName) ? Validity.ALLOWED : Validity.INDETERMINATE;
        }

        @Override
        public Validity validateSubType(DatabindContext context, JavaType baseType, JavaType subType) {
            return allowlist.isAlwaysAllowedType(subType.getRawClass()) ? Validity.ALLOWED : Validity.DENIED;
        }
    }
}
