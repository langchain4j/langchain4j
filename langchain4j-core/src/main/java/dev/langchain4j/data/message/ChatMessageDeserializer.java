package dev.langchain4j.data.message;

import com.google.gson.reflect.TypeToken;

import java.util.List;

import static dev.langchain4j.data.message.ChatMessageSerializer.GSON;
import static java.util.Collections.emptyList;

public class ChatMessageDeserializer {

    /**
     * Deserializes a JSON string into a {@link ChatMessage}.
     *
     * @param json The JSON string representing a chat message.
     * @return A {@link ChatMessage} deserialized from the provided JSON string.
     * @see ChatMessageSerializer For details on serialization.
     */
    public static ChatMessage messageFromJson(String json) {
        return GSON.fromJson(json, ChatMessage.class);
    }

    /**
     * Deserializes a JSON string into a list of {@link ChatMessage}.
     *
     * @param json The JSON string containing an array of chat messages.
     * @return A list of {@link ChatMessage} deserialized from the provided JSON string.
     * @see ChatMessageSerializer For details on serialization.
     */
    public static List<ChatMessage> messagesFromJson(String json) {
        List<ChatMessage> messages = GSON.fromJson(json, new TypeToken<List<ChatMessage>>() {
        }.getType());
        return messages == null ? emptyList() : messages;
    }
}
