package dev.langchain4j.agentic.scope;

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
import dev.langchain4j.agentic.scope.DefaultAgenticScope.AgentMessage;
import dev.langchain4j.agentic.scope.DefaultAgenticScope.Kind;
import dev.langchain4j.agentic.internal.AgentInvocation;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.JacksonChatMessageJsonCodec;

import java.util.Collection;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@Internal
class JacksonAgenticScopeJsonCodec implements AgenticScopeJsonCodec {

    static JsonMapper.Builder agenticScopeJsonMapperBuilder() {
        return JacksonChatMessageJsonCodec.chatMessageJsonMapperBuilder()
                .addMixIn(DefaultAgenticScope.class, AgenticScopeMixin.class)
                .addMixIn(AgentMessage.class, AgentMessageMixin.class)
                .addMixIn(AgentInvocation.class, AgentInvocationMixin.class);
    }

    static ObjectMapper agenticScopeJsonSerializer() {
        ObjectMapper mapper = agenticScopeJsonMapperBuilder().build();

        // Configure the ObjectMapper to add type information for users types
        // At the moment, this is only needed and used for enums, but we could add support for other types in the future
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

    private static final ObjectMapper SERIALIZER_MAPPER = agenticScopeJsonSerializer();
    private static final ObjectMapper DESERIALIZER_MAPPER = agenticScopeJsonMapperBuilder().build();

    @Override
    public DefaultAgenticScope fromJson(String json) {
        try {
            return DESERIALIZER_MAPPER.readValue(json, DefaultAgenticScope.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize AgenticScope from JSON", e);
        }
    }

    @Override
    public String toJson(DefaultAgenticScope agenticScope) {
        try {
            return SERIALIZER_MAPPER.writeValueAsString(agenticScope);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize AgenticScope to JSON", e);
        }
    }

    @JsonInclude(NON_NULL)
    private static abstract class AgenticScopeMixin {
        @JsonCreator
        public AgenticScopeMixin(
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
    private static abstract class AgentInvocationMixin {
        @JsonCreator
        public AgentInvocationMixin(
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
                    className.startsWith("dev.langchain4j.agentic.scope.") ||
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
