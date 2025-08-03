package dev.langchain4j.agentic.cognisphere;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DatabindContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.TypeIdResolver;
import com.fasterxml.jackson.databind.jsontype.impl.StdTypeResolverBuilder;
import com.fasterxml.jackson.databind.jsontype.impl.TypeIdResolverBase;
import dev.langchain4j.Internal;
import dev.langchain4j.agentic.cognisphere.DefaultCognisphere.AgentMessage;
import dev.langchain4j.agentic.cognisphere.DefaultCognisphere.Kind;
import dev.langchain4j.agentic.internal.AgentCall;
import dev.langchain4j.data.message.ChatMessage;

import java.util.Collection;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@Internal
class JacksonCognisphereJsonCodec implements CognisphereJsonCodec {

    static JsonMapper.Builder cognisphereJsonMapperBuilder() {
        return JacksonChatMessageJsonCodec.chatMessageJsonMapperBuilder()
                .addMixIn(DefaultCognisphere.class, CognisphereMixin.class)
                .addMixIn(AgentMessage.class, AgentMessageMixin.class)
                .addMixIn(AgentCall.class, AgentCallMixin.class);
    }

    static ObjectMapper cognisphereJsonSerializer() {
        ObjectMapper mapper = cognisphereJsonMapperBuilder().build();

        mapper.activateDefaultTyping(
                BasicPolymorphicTypeValidator.builder()
                        .allowIfBaseType(Object.class)
                        .build(),
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.WRAPPER_OBJECT
        );

        CustomTypeResolverBuilder typeResolverBuilder = new CustomTypeResolverBuilder();
        typeResolverBuilder.init(JsonTypeInfo.Id.CLASS, null);
        typeResolverBuilder.inclusion(JsonTypeInfo.As.WRAPPER_OBJECT);
        mapper.setDefaultTyping(typeResolverBuilder);

        return mapper;
    }

    private static final ObjectMapper SERIALIZER_MAPPER = cognisphereJsonSerializer();
    private static final ObjectMapper DESERIALIZER_MAPPER = cognisphereJsonMapperBuilder().build();

    @Override
    public DefaultCognisphere fromJson(String json) {
        try {
            return DESERIALIZER_MAPPER.readValue(json, DefaultCognisphere.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize Cognisphere from JSON", e);
        }
    }

    @Override
    public String toJson(DefaultCognisphere cognisphere) {
        try {
            return SERIALIZER_MAPPER.writeValueAsString(cognisphere);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize Cognisphere to JSON", e);
        }
    }

    @JsonInclude(NON_NULL)
    private static abstract class CognisphereMixin {
        @JsonCreator
        public CognisphereMixin(
                @JsonProperty("memoryId") Object memoryId,
                @JsonProperty("kind") Kind kind) {
        }
    }

    @JsonInclude(NON_NULL)
    private static abstract class AgentMessageMixin {
        @JsonCreator
        public AgentMessageMixin(
                @JsonProperty("agentName") String agentName,
                @JsonProperty("message") ChatMessage message) {
        }
    }

    @JsonInclude(NON_NULL)
    private static abstract class AgentCallMixin {
        @JsonCreator
        public AgentCallMixin(
                @JsonProperty("agentName") String agentName,
                @JsonProperty("input") Object[] input,
                @JsonProperty("output") Object output) {
        }
    }

    public static class CustomTypeIdResolver extends TypeIdResolverBase {

        @Override
        public String idFromValue(Object value) {
            return idFromValueAndType(value, value.getClass());
        }

        @Override
        public String idFromValueAndType(Object value, Class<?> suggestedType) {
            String className = suggestedType.getName();
            return isBuiltInType(className) ? null : className;
        }

        private boolean isBuiltInType(String className) {
            return className.startsWith("java.") ||
                    className.startsWith("[Ljava.") ||
                    className.startsWith("dev.langchain4j.data.message.") ||
                    className.startsWith("dev.langchain4j.agentic.cognisphere.") ||
                    className.startsWith("dev.langchain4j.agentic.internal.");
        }

        @Override
        public JavaType typeFromId(DatabindContext context, String id) {
            if (id == null) {
                return null;
            }
            try {
                Class<?> clazz = Class.forName(id);
                return context.constructType(clazz);
            } catch (ClassNotFoundException e) {
                if (id.equals("id")) {
                    return context.constructType(String.class);
                }
                return null;
            }
        }

        @Override
        public JsonTypeInfo.Id getMechanism() {
            return JsonTypeInfo.Id.CLASS;
        }
    }

    // Custom TypeResolverBuilder
    public static class CustomTypeResolverBuilder extends StdTypeResolverBuilder {

        @Override
        protected TypeIdResolver idResolver(MapperConfig<?> config, JavaType baseType,
                                            PolymorphicTypeValidator subtypeValidator, Collection<NamedType> subtypes,
                                            boolean forSer, boolean forDeser) {
            return new CustomTypeIdResolver();
        }
    }
}
