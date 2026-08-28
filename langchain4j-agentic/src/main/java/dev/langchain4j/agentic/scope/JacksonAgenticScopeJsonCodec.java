package dev.langchain4j.agentic.scope;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidTypeIdException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import dev.langchain4j.Internal;
import dev.langchain4j.agentic.scope.DefaultAgenticScope.AgentMessage;
import dev.langchain4j.agentic.scope.DefaultAgenticScope.Kind;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.JacksonChatMessageJsonCodec;

import java.util.Map;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@Internal
class JacksonAgenticScopeJsonCodec implements ConfigurableAgenticScopeJsonCodec {

    private static final ConfigurablePolymorphicTypeValidator PTV = new ConfigurablePolymorphicTypeValidator();

    private static final ObjectMapper MAPPER = agenticScopeJsonSerializer();

    static JsonMapper.Builder agenticScopeJsonMapperBuilder() {
        return JacksonChatMessageJsonCodec.chatMessageJsonMapperBuilder()
                .addMixIn(DefaultAgenticScope.class, AgenticScopeMixin.class)
                .addMixIn(AgentMessage.class, AgentMessageMixin.class)
                .addMixIn(AgentInvocation.class, AgentInvocationMixin.class);
    }


    static ObjectMapper agenticScopeJsonSerializer() {
        ObjectMapper mapper = agenticScopeJsonMapperBuilder().build();
        mapper.activateDefaultTyping(PTV);
        return mapper;
    }

    @Override
    public void allowDeserializationPackagePrefix(final String packagePrefix) {
        PTV.addAllowedPrefix(packagePrefix);
    }

    @Override
    public void allowDeserializationType(final Class<?> type) {
        PTV.addAllowedClass(type.getName());
    }

    @Override
    public void withClassLoader(ClassLoader classloader) {
        MAPPER.setTypeFactory(MAPPER.getTypeFactory().withClassLoader(classloader));
    }

    @Override
    public void registerForDeserializationPackageOf(final Class<?> type) {
        String packageName = type.getPackageName();
        allowDeserializationPackagePrefix(packageName + ".");
        withClassLoader(type.getClassLoader());
    }

    @Override
    public ObjectMapper objectMapper() {
        return MAPPER;
    }

    @Override
    public DefaultAgenticScope fromJson(String json) {
        try {
            return MAPPER.readValue(json, DefaultAgenticScope.class);
        } catch (InvalidTypeIdException e) {
            throw new UnserializableAgenticScopeException(e.getTypeId(), e);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize AgenticScope from JSON", e);
        }
    }

    @Override
    public String toJson(DefaultAgenticScope agenticScope) {
        try {
            return MAPPER.writeValueAsString(agenticScope.serializableCopy());
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
