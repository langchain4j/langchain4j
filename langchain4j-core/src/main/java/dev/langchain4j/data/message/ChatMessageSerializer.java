package dev.langchain4j.data.message;

import dev.langchain4j.spi.ServiceHelper;
import dev.langchain4j.spi.data.message.ChatMessageJsonCodecFactory;
import java.util.List;

/**
 * A serializer for {@link ChatMessage} objects.
 */
public class ChatMessageSerializer {
    private ChatMessageSerializer() {}

    /**
     * The {@link ChatMessageJsonCodec} instance used for serialization and deserialization.
     */
    static final ChatMessageJsonCodec CODEC = ServiceHelper.loadFactoryService(
            ChatMessageJsonCodecFactory.class,
            ChatMessageJsonCodecFactory::create,
            GsonChatMessageJsonCodec::new);

    /**
     * Serializes a chat message into a JSON string.
     *
     * @param message Chat message to be serialized.
     * @return A JSON string with the contents of the message.
     * @see ChatMessageDeserializer For details on deserialization.
     */
    public static String messageToJson(ChatMessage message) {
        return CODEC.messageToJson(message);
    }

    /**
     * Serializes a list of chat messages into a JSON string.
     *
     * @param messages The list of chat messages to be serialized.
     * @return A JSON string representing provided chat messages.
     * @see ChatMessageDeserializer For details on deserialization.
     */
    public static String messagesToJson(List<ChatMessage> messages) {
        return CODEC.messagesToJson(messages);
    }
}
