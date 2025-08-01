package dev.langchain4j.agentic.cognisphere;

import dev.langchain4j.Internal;

/**
 * A codec for serializing and deserializing {@link DefaultCognisphere} objects to and from JSON.
 */
@Internal
public interface CognisphereJsonCodec {

    /**
     * Deserializes a JSON string to a {@link DefaultCognisphere} object.
     * @param json the JSON string.
     * @return the deserialized {@link DefaultCognisphere} object.
     */
    DefaultCognisphere fromJson(String json);

    /**
     * Serializes a {@link DefaultCognisphere} object to a JSON string.
     * @param cognisphere the {@link DefaultCognisphere} object.
     * @return the serialized JSON string.
     */
    String toJson(DefaultCognisphere cognisphere);
}
