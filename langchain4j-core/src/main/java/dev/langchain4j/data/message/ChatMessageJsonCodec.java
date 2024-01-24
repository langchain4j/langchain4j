package dev.langchain4j.data.message;

import java.util.List;

/**
 * A codec for serializing and deserializing {@link ChatMessage} objects to and from JSON.
 */
public interface ChatMessageJsonCodec {

    /**
     * Deserializes a JSON string to a {@link ChatMessage} object.
     * @param json the JSON string.
     * @return the deserialized {@link ChatMessage} object.
     */
    ChatMessage messageFromJson(String json);

    /**
     * Deserializes a JSON string to a list of {@link ChatMessage} objects.
     * @param json the JSON string.
     * @return the deserialized list of {@link ChatMessage} objects.
     */
    List<ChatMessage> messagesFromJson(String json);

    /**
     * Serializes a {@link ChatMessage} object to a JSON string.
     * @param message the {@link ChatMessage} object.
     * @return the serialized JSON string.
     */
    String messageToJson(ChatMessage message);

    /**
     * Serializes a list of {@link ChatMessage} objects to a JSON string.
     * @param messages the list of {@link ChatMessage} objects.
     * @return the serialized JSON string.
     */
    String messagesToJson(List<ChatMessage> messages);
}
