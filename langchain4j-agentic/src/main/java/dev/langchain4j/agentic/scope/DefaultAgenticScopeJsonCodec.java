package dev.langchain4j.agentic.scope;

import dev.langchain4j.Internal;
import dev.langchain4j.agentic.internal.PendingResponse;
import dev.langchain4j.agentic.internal.SuspendedResponse;
import dev.langchain4j.exception.JsonTypeNotAllowedException;
import dev.langchain4j.internal.Json;
import dev.langchain4j.internal.StateJson;
import dev.langchain4j.internal.TypeAllowlist;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * Persists an {@link AgenticScope} through whichever JSON library LangChain4j is configured with.
 *
 * <p>Agent state holds whatever the agents put in it, so the document names the types it contains
 * and reading it means instantiating them - hence the allowlist. Everything JSON-library-specific
 * about that lives behind {@link StateJson}, which is why this module needs no JSON library
 * of its own.
 */
@Internal
class DefaultAgenticScopeJsonCodec implements AgenticScopeJsonCodec {

    /**
     * One list per JVM: an application registers a type once and it holds for every later
     * deserialization, which is what {@code AgenticScopeSerializer} documents.
     */
    private static final TypeAllowlist ALLOWLIST = agenticScopeAllowlist();

    /**
     * Replaced rather than reconfigured when the class loader changes: a codec that is already
     * serving other threads must not be mutated, and a Jackson 3 mapper cannot be reconfigured
     * once built.
     */
    private static volatile Json.JsonCodec codec = StateJson.codec(ALLOWLIST);

    private static TypeAllowlist agenticScopeAllowlist() {
        TypeAllowlist allowlist = new TypeAllowlist();
        allowlist.addAllowedClass(DefaultAgenticScope.AgentMessage.class.getName());
        allowlist.addAllowedClass(AgentInvocation.class.getName());
        allowlist.addAllowedClass(PendingResponse.class.getName());
        allowlist.addAllowedClass(SuspendedResponse.class.getName());
        // the persistable form of the answers written to the scope by decision agents
        allowlist.addAllowedClass(PersistedDecisionAnswers.PersistedYesNoAnswer.class.getName());
        allowlist.addAllowedClass(PersistedDecisionAnswers.PersistedChoice.class.getName());
        allowlist.addAllowedClass(PersistedDecisionAnswers.PersistedScale.class.getName());
        return allowlist;
    }

    @Override
    public DefaultAgenticScope fromJson(String json) {
        try {
            DefaultAgenticScope agenticScope = codec.fromJson(json, DefaultAgenticScope.class);
            replaceValues(agenticScope, PersistedDecisionAnswers::fromPersisted);
            return agenticScope;
        } catch (JsonTypeNotAllowedException e) {
            throw new UnserializableAgenticScopeException(e.typeId(), e);
        } catch (RuntimeException e) {
            // The exception this has always thrown, so persistence behaves the same whichever
            // JSON library is underneath.
            throw new RuntimeException("Failed to deserialize AgenticScope from JSON", e);
        }
    }

    @Override
    public String toJson(DefaultAgenticScope agenticScope) {
        try {
            DefaultAgenticScope copy = agenticScope.serializableCopy();
            replaceValues(copy, PersistedDecisionAnswers::toPersisted);
            return codec.toJson(copy);
        } catch (RuntimeException e) {
            throw new RuntimeException("Failed to serialize AgenticScope to JSON", e);
        }
    }

    /**
     * Replaces every value of the state of the given scope, and every input and output of its registered
     * invocations, with the one returned by the converter.
     */
    private static void replaceValues(DefaultAgenticScope agenticScope, UnaryOperator<Object> converter) {
        agenticScope.state().replaceAll((key, value) -> converter.apply(value));
        agenticScope.replaceAgentInvocations(invocation -> replaceValues(invocation, converter));
    }

    /**
     * The given invocation with its input and output values converted, or the invocation itself when the converter
     * leaves all of them as they are, so that an invocation without values to adapt is persisted as it was recorded.
     */
    private static AgentInvocation replaceValues(AgentInvocation invocation, UnaryOperator<Object> converter) {
        boolean changed = false;
        Map<String, Object> input = invocation.input();
        if (input != null) {
            Map<String, Object> convertedInput = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : input.entrySet()) {
                Object converted = converter.apply(entry.getValue());
                changed |= converted != entry.getValue();
                convertedInput.put(entry.getKey(), converted);
            }
            if (changed) {
                input = convertedInput;
            }
        }
        Object output = converter.apply(invocation.output());
        if (!changed && output == invocation.output()) {
            return invocation;
        }
        return new AgentInvocation(
                invocation.agentType(),
                invocation.agentName(),
                invocation.agentId(),
                input,
                output,
                invocation.skipReason());
    }

    @Override
    public boolean withClassLoader(ClassLoader classLoader) {
        codec = StateJson.codec(ALLOWLIST, classLoader);
        return true;
    }

    @Override
    public boolean allowPackagePrefix(String packagePrefix) {
        ALLOWLIST.addAllowedPrefix(packagePrefix);
        return true;
    }

    @Override
    public boolean allowType(Class<?> type) {
        ALLOWLIST.addAllowedClass(type.getName());
        return true;
    }
}
