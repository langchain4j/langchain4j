package dev.langchain4j.agentic.cognisphere;

import dev.langchain4j.Internal;

/**
 * A codec for serializing and deserializing {@link Cognisphere} objects to and from JSON.
 */
@Internal
public interface CognisphereJsonCodec {

    /**
     * Deserializes a JSON string to a {@link Cognisphere} object.
     * @param json the JSON string.
     * @return the deserialized {@link Cognisphere} object.
     */
    Cognisphere fromJson(String json);

    /**
     * Serializes a {@link Cognisphere} object to a JSON string.
     * @param cognisphere the {@link Cognisphere} object.
     * @return the serialized JSON string.
     */
    String toJson(Cognisphere cognisphere);
}
