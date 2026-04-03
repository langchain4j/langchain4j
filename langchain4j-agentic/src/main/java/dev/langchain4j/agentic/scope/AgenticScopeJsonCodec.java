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
}
