package dev.langchain4j.data.message;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;

/**
 * Represents a chat message.
 * Used together with {@link ChatLanguageModel} and {@link StreamingChatLanguageModel}.
 *
 * @see SystemMessage
 * @see UserMessage
 * @see AiMessage
 * @see ToolExecutionResultMessage
 */
public interface ChatMessage {

    /**
     * The type of the message.
     *
     * @return the type of the message
     */
    ChatMessageType type();
}
