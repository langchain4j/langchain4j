package dev.langchain4j.model.chat.request;

/**
 * A deserializer for {@link ChatRequestParameters} objects.
 */
public class ChatRequestParametersDeserializer {

    private static final ChatRequestParametersJsonCodec CODEC = new JacksonChatRequestParametersJsonCodec();

    /**
     * Deserializes a JSON string into a {@link ChatRequestParameters}.
     *
     * @param json The JSON string representing a chat message.
     * @return A {@link ChatRequestParameters} deserialized from the provided JSON string.
     */
    public static ChatRequestParameters chatRequestParametersFromJson(String json) {
        return CODEC.chatParametersFromJson(json);
    }
}
