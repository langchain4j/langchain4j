package dev.langchain4j.data.message;

import static dev.langchain4j.data.message.ChatMessageSerializer.CODEC;
import java.util.List;

/**
 * A deserializer for {@link ChatMessage} objects.
 */
public class ChatMessageDeserializer {
    private ChatMessageDeserializer() {}

    /**
     * Deserializes a JSON string into a {@link ChatMessage}.
     *
     * @param json The JSON string representing a chat message.
     * @return A {@link ChatMessage} deserialized from the provided JSON string.
     * @see ChatMessageSerializer For details on serialization.
     */
    public static ChatMessage messageFromJson(String json) {
        return CODEC.messageFromJson(json);
    }

    /**
     * Deserializes a JSON string into a list of {@link ChatMessage}.
     *
     * @param json The JSON string containing an array of chat messages.
     * @return A list of {@link ChatMessage} deserialized from the provided JSON string.
     * @see ChatMessageSerializer For details on serialization.
     */
    public static List<ChatMessage> messagesFromJson(String json) {
        return CODEC.messagesFromJson(json);
    }
}
