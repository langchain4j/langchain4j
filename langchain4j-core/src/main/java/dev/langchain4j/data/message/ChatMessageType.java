package dev.langchain4j.data.message;

public enum ChatMessageType {

    SYSTEM(SystemMessage.class),
    USER(UserMessage.class),
    AI(AiMessage.class),
    TOOL_EXECUTION_RESULT(ToolExecutionResultMessage.class);

    private final Class<? extends ChatMessage> messageClass;

    ChatMessageType(Class<? extends ChatMessage> messageClass) {
        this.messageClass = messageClass;
    }

    public Class<? extends ChatMessage> messageClass() {
        return messageClass;
    }
}
