package dev.langchain4j.json.jackson3;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.Internal;
import dev.langchain4j.agentic.scope.AgentInvocation;
import dev.langchain4j.agentic.scope.AgenticScopeJsonCodec;
import dev.langchain4j.agentic.scope.AgenticScopeTypeAllowlist;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.agentic.scope.DefaultAgenticScope.AgentMessage;
import dev.langchain4j.agentic.scope.DefaultAgenticScope.Kind;
import dev.langchain4j.agentic.scope.UnserializableAgenticScopeException;
import dev.langchain4j.data.message.ChatMessage;
import java.util.Map;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DatabindContext;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.exc.InvalidTypeIdException;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.PolymorphicTypeValidator;

/**
 * Jackson 3 codec for {@link DefaultAgenticScope} persistence, resolved through the
 * {@code ServiceLoader} when this module is on the classpath.
 *
 * <p>It shares {@link AgenticScopeTypeAllowlist} with the Jackson 2 codec, so a type an application
 * registers through {@code AgenticScopeSerializer} is allowed whichever codec is active, and there
 * is only one list to get right.
 */
@Internal
public class Jackson3AgenticScopeJsonCodec implements AgenticScopeJsonCodec {

    private static final ObjectMapper MAPPER = Jackson3ChatMessageJsonCodec.chatMessageJsonMapperBuilder()
            .addMixIn(DefaultAgenticScope.class, AgenticScopeMixin.class)
            .addMixIn(AgentMessage.class, AgentMessageMixin.class)
            .addMixIn(AgentInvocation.class, AgentInvocationMixin.class)
            .activateDefaultTyping(new AllowlistTypeValidator())
            .build();

    @Override
    public DefaultAgenticScope fromJson(String json) {
        try {
            return MAPPER.readValue(json, DefaultAgenticScope.class);
        } catch (InvalidTypeIdException e) {
            throw new UnserializableAgenticScopeException(e.getTypeId(), e);
        } catch (JacksonException e) {
            throw new RuntimeException("Failed to deserialize AgenticScope from JSON", e);
        }
    }

    @Override
    public String toJson(DefaultAgenticScope agenticScope) {
        try {
            return MAPPER.writeValueAsString(agenticScope.serializableCopy());
        } catch (JacksonException e) {
            throw new RuntimeException("Failed to serialize AgenticScope to JSON", e);
        }
    }

    @Override
    public boolean allowPackagePrefix(String packagePrefix) {
        AgenticScopeTypeAllowlist.INSTANCE.addAllowedPrefix(packagePrefix);
        return true;
    }

    @Override
    public boolean allowType(Class<?> type) {
        AgenticScopeTypeAllowlist.INSTANCE.addAllowedClass(type.getName());
        return true;
    }

    /** Adapts {@link AgenticScopeTypeAllowlist} to Jackson 3, as its Jackson 2 twin does. */
    private static final class AllowlistTypeValidator extends PolymorphicTypeValidator {

        @Override
        public Validity validateBaseType(DatabindContext context, JavaType baseType) {
            return Validity.INDETERMINATE;
        }

        @Override
        public Validity validateSubClassName(DatabindContext context, JavaType baseType, String subClassName) {
            return AgenticScopeTypeAllowlist.INSTANCE.isAllowedTypeId(subClassName)
                    ? Validity.ALLOWED
                    : Validity.INDETERMINATE;
        }

        @Override
        public Validity validateSubType(DatabindContext context, JavaType baseType, JavaType subType) {
            return AgenticScopeTypeAllowlist.INSTANCE.isAlwaysAllowedType(subType.getRawClass())
                    ? Validity.ALLOWED
                    : Validity.DENIED;
        }
    }

    @JsonInclude(NON_NULL)
    private abstract static class AgenticScopeMixin {
        @JsonCreator
        AgenticScopeMixin(@JsonProperty("memoryId") Object memoryId, @JsonProperty("kind") Kind kind) {}
    }

    @JsonInclude(NON_NULL)
    private abstract static class AgentMessageMixin {
        @JsonCreator
        AgentMessageMixin(
                @JsonProperty("agentName") String agentName,
                @JsonProperty("agentId") String agentId,
                @JsonProperty("message") ChatMessage message) {}
    }

    @JsonInclude(NON_NULL)
    private abstract static class AgentInvocationMixin {
        @JsonCreator
        AgentInvocationMixin(
                @JsonProperty("agentType") Class<?> agentType,
                @JsonProperty("agentName") String agentName,
                @JsonProperty("agentId") String agentId,
                @JsonProperty("input") Map<String, Object> input,
                @JsonProperty("output") Object output) {}
    }
}
