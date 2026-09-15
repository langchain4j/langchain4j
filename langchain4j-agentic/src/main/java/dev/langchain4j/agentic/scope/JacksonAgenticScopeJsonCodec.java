package dev.langchain4j.agentic.scope;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidTypeIdException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import dev.langchain4j.Internal;
import dev.langchain4j.agentic.scope.DefaultAgenticScope.AgentMessage;
import dev.langchain4j.agentic.scope.DefaultAgenticScope.Kind;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.JacksonChatMessageJsonCodec;

import java.util.Map;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@Internal
class JacksonAgenticScopeJsonCodec implements AgenticScopeJsonCodec {

    private static final ConfigurablePolymorphicTypeValidator PTV = new ConfigurablePolymorphicTypeValidator();

    /**
     * Rebuilt rather than reconfigured when the class loader changes: a mapper that is already
     * serving other threads must not be mutated, and the successor to this codec will be built on
     * a Jackson version whose mapper cannot be mutated at all.
     */
    private static volatile ObjectMapper mapper = agenticScopeJsonSerializer();

    static JsonMapper.Builder agenticScopeJsonMapperBuilder() {
        return JacksonChatMessageJsonCodec.chatMessageJsonMapperBuilder()
                .addMixIn(DefaultAgenticScope.class, AgenticScopeMixin.class)
                .addMixIn(AgentMessage.class, AgentMessageMixin.class)
                .addMixIn(AgentInvocation.class, AgentInvocationMixin.class);
    }


    static ObjectMapper agenticScopeJsonSerializer() {
        return agenticScopeJsonSerializer(null);
    }

    private static ObjectMapper agenticScopeJsonSerializer(ClassLoader classLoader) {
        JsonMapper.Builder builder = agenticScopeJsonMapperBuilder();
        if (classLoader != null) {
            builder.typeFactory(TypeFactory.defaultInstance().withClassLoader(classLoader));
        }
        ObjectMapper newMapper = builder.build();
        newMapper.activateDefaultTyping(PTV);
        return newMapper;
    }

    @Override
    public boolean allowPackagePrefix(final String packagePrefix) {
        PTV.addAllowedPrefix(packagePrefix);
        return true;
    }

    @Override
    public boolean allowType(final Class<?> type) {
        PTV.addAllowedClass(type.getName());
        return true;
    }

    @Override
    public boolean withClassLoader(ClassLoader classLoader) {
        mapper = agenticScopeJsonSerializer(classLoader);
        return true;
    }

    @Override
    public DefaultAgenticScope fromJson(String json) {
        try {
            return mapper.readValue(json, DefaultAgenticScope.class);
        } catch (InvalidTypeIdException e) {
            throw new UnserializableAgenticScopeException(e.getTypeId(), e);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize AgenticScope from JSON", e);
        }
    }

    @Override
    public String toJson(DefaultAgenticScope agenticScope) {
        try {
            return mapper.writeValueAsString(agenticScope.serializableCopy());
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
                @JsonProperty("agentId") String agentId,
                @JsonProperty("message") ChatMessage message) {
        }
    }

    @JsonInclude(NON_NULL)
    private static abstract class AgentInvocationMixin {
        @JsonCreator
        public AgentInvocationMixin(
                @JsonProperty("agentType") Class<?> agentType,
                @JsonProperty("agentName") String agentName,
                @JsonProperty("agentId") String agentId,
                @JsonProperty("input") Map<String, Object> input,
                @JsonProperty("output") Object output) {
        }
    }
}
