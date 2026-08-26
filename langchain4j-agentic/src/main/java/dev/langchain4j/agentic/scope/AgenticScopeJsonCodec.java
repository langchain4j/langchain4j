package dev.langchain4j.agentic.scope;

import dev.langchain4j.Internal;

/**
 * A codec for serializing and deserializing {@link DefaultAgenticScope} objects to and from JSON.
 */
@Internal
public interface AgenticScopeJsonCodec {

    /**
     * Deserializes a JSON string to a {@link DefaultAgenticScope} object.
     * @param json the JSON string.
     * @return the deserialized {@link DefaultAgenticScope} object.
     */
    DefaultAgenticScope fromJson(String json);

    /**
     * Serializes a {@link DefaultAgenticScope} object to a JSON string.
     * @param agenticScope the {@link DefaultAgenticScope} object.
     * @return the serialized JSON string.
     */
    String toJson(DefaultAgenticScope agenticScope);

    /**
     * Allows every class under the given package prefix to be deserialized as part of an
     * {@link AgenticScope}'s state.
     *
     * <p>Deserializing arbitrary types by name is how polymorphic deserialization becomes a
     * vulnerability, so a codec that supports it keeps an allowlist. A codec that does not
     * support allowlisting should return {@code false} rather than silently accept everything.
     *
     * @return whether this codec applied the registration.
     */
    default boolean allowPackagePrefix(String packagePrefix) {
        return false;
    }

    /**
     * Allows a single class to be deserialized as part of an {@link AgenticScope}'s state.
     *
     * @return whether this codec applied the registration.
     * @see #allowPackagePrefix(String)
     */
    default boolean allowType(Class<?> type) {
        return false;
    }
}
