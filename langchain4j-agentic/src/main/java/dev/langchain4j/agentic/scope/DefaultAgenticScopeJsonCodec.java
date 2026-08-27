package dev.langchain4j.agentic.scope;

import dev.langchain4j.Internal;
import dev.langchain4j.agentic.internal.PendingResponse;
import dev.langchain4j.agentic.internal.SuspendedResponse;
import dev.langchain4j.exception.JsonTypeNotAllowedException;
import dev.langchain4j.internal.Json;
import dev.langchain4j.internal.PolymorphicJson;
import dev.langchain4j.internal.TypeAllowlist;

/**
 * Persists an {@link AgenticScope} through whichever JSON library LangChain4j is configured with.
 *
 * <p>Agent state holds whatever the agents put in it, so the document names the types it contains
 * and reading it means instantiating them - hence the allowlist. Everything JSON-library-specific
 * about that lives behind {@link PolymorphicJson}, which is why this module needs no JSON library
 * of its own.
 */
@Internal
class DefaultAgenticScopeJsonCodec implements AgenticScopeJsonCodec {

    /**
     * One list per JVM: an application registers a type once and it holds for every later
     * deserialization, which is what {@code AgenticScopeSerializer} documents.
     */
    private static final TypeAllowlist ALLOWLIST = agenticScopeAllowlist();

    private static final Json.JsonCodec CODEC = PolymorphicJson.codec(ALLOWLIST);

    private static TypeAllowlist agenticScopeAllowlist() {
        TypeAllowlist allowlist = new TypeAllowlist();
        allowlist.addAllowedClass(DefaultAgenticScope.AgentMessage.class.getName());
        allowlist.addAllowedClass(AgentInvocation.class.getName());
        allowlist.addAllowedClass(PendingResponse.class.getName());
        allowlist.addAllowedClass(SuspendedResponse.class.getName());
        return allowlist;
    }

    @Override
    public DefaultAgenticScope fromJson(String json) {
        try {
            return CODEC.fromJson(json, DefaultAgenticScope.class);
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
            return CODEC.toJson(agenticScope.serializableCopy());
        } catch (RuntimeException e) {
            throw new RuntimeException("Failed to serialize AgenticScope to JSON", e);
        }
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
