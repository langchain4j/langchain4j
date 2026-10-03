package dev.langchain4j.data.message;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;

/**
 * Represents a chat message.
 * Used together with {@link ChatModel} and {@link StreamingChatModel}.
 *
 * @see SystemMessage
 * @see UserMessage
 * @see AiMessage
 * @see ToolExecutionResultMessage
 * @see CustomMessage
 */
public interface ChatMessage {

    /**
     * The type of the message.
     *
     * @return the type of the message
     */
    ChatMessageType type();

    /**
     * Returns the text representation of this message.
     *
     * @return the text of this message.
     * @throws UnsupportedOperationException if this message type does not have a text representation.
     */
    default String text() {
        throw new UnsupportedOperationException("Not implemented for message type: " + type());
    }
}
