package dev.langchain4j.data.message;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.List;

public class ChatMessageSerializer {

    static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(ChatMessage.class, new GsonChatMessageAdapter())
            .registerTypeAdapter(SystemMessage.class, new GsonChatMessageAdapter())
            .registerTypeAdapter(UserMessage.class, new GsonChatMessageAdapter())
            .registerTypeAdapter(AiMessage.class, new GsonChatMessageAdapter())
            .registerTypeAdapter(ToolExecutionResultMessage.class, new GsonChatMessageAdapter())
            .create();

    /**
     * Serializes a chat message into a JSON string.
     *
     * @param message Chat message to be serialized.
     * @return A JSON string with the contents of the message.
     * @see ChatMessageDeserializer For details on deserialization.
     */
    public static String messageToJson(ChatMessage message) {
        return GSON.toJson(message);
    }

    /**
     * Serializes a list of chat messages into a JSON string.
     *
     * @param messages The list of chat messages to be serialized.
     * @return A JSON string representing provided chat messages.
     * @see ChatMessageDeserializer For details on deserialization.
     */
    public static String messagesToJson(List<ChatMessage> messages) {
        return GSON.toJson(messages);
    }
}
