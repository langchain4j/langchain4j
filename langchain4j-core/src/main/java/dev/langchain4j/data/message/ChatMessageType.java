package dev.langchain4j.data.message;

/**
 * The type of content, e.g. text or image.
 * Maps to implementations of {@link ChatMessage}.
 */
public enum ChatMessageType {
    /**
     * A message from the system, typically defined by a developer.
     */
    SYSTEM(SystemMessage.class),

    /**
     * A message from the user.
     */
    USER(UserMessage.class),

    /**
     * A message from the AI.
     */
    AI(AiMessage.class),

    /**
     * A message from a tool.
     */
    TOOL_EXECUTION_RESULT(ToolExecutionResultMessage.class);

    private final Class<? extends ChatMessage> messageClass;

    ChatMessageType(Class<? extends ChatMessage> messageClass) {
        this.messageClass = messageClass;
    }

    /**
     * Returns the class of the message type.
     * @return the class of the message type.
     */
    public Class<? extends ChatMessage> messageClass() {
        return messageClass;
    }
}
