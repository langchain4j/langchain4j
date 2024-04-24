package dev.langchain4j.store.memory.chat.astradb;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A chat message stored in AstraDB.
 */
@Setter @Getter @NoArgsConstructor
public class AstraDBChatMessage implements ChatMessage {

    /** Public Static to help build filters if any. */
    public static final String PROP_CHAT_ID = "chat_id";

    /** Public Static to help build filters if any. */
    public static final String PROP_MESSAGE = "message";

    /** Public Static to help build filters if any. */
    public static final String PROP_MESSAGE_TIME = "message_time";

    /** Public Static to help build filters if any. */
    public static final String PROP_MESSAGE_TYPE = "message_type";

    @JsonProperty(PROP_CHAT_ID)
    private String chatId;

    @JsonProperty(PROP_MESSAGE)
    private String message;

    @JsonProperty(PROP_MESSAGE_TYPE)
    private String messageType;

    @JsonProperty(PROP_MESSAGE_TIME)
    private Instant messageTime;

    /** {@inheritDoc} */
    @Override
    public ChatMessageType type() {
        if ("SYSTEM".equals(messageType)) {
            return ChatMessageType.SYSTEM;
        }
        if ("USER".equals(messageType)) {
            return ChatMessageType.USER;
        }
        if ("AI".equals(messageType)) {
            return ChatMessageType.AI;
        }
        if ("TOOL_EXECUTION_RESULT".equals(messageType)) {
            return ChatMessageType.TOOL_EXECUTION_RESULT;
        }
        throw new IllegalArgumentException("Cannot convert to ChatMessageType from  " + messageType);
    }

    /**
     * Downcast to {@link ChatMessage}.
     *
     * @return
     *      chatMessage interface
     */
    public ChatMessage toChatMessage() {
        if ("SYSTEM".equals(messageType)) {
            return new SystemMessage(message);
        } else if ("USER".equals(messageType)) {
            return new UserMessage(message);
        } else if ("AI".equals(messageType)) {
            return new AiMessage(message);
        }
        return this;
    }

    /** {@inheritDoc} */
    @Override
    public String text() {
        return message;
    }
}
