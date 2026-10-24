package dev.langchain4j.model.chat.request;

import dev.langchain4j.Internal;

/**
 * A codec for deserializing {@link ChatRequestParameters} objects from JSON.
 */
@Internal
public interface ChatRequestParametersJsonCodec {

    /**
     * Deserializes a JSON string to a {@link ChatRequestParameters} object.
     * @param json the JSON string.
     * @return the deserialized {@link ChatRequestParameters} object.
     */
    ChatRequestParameters chatParametersFromJson(String json);
}
