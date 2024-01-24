package dev.langchain4j.data.message;

/**
 * A chat message.
 */
public interface ChatMessage {

    /**
     * The type of the message.
     * @return the type of the message
     */
    ChatMessageType type();

    /**
     * The text of the message.
     *
     * @deprecated decode through {@link #type()} instead.
     * @return the text of the message
     */
    @Deprecated
    String text();
}