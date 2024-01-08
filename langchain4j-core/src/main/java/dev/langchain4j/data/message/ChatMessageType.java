package dev.langchain4j.data.message;

import static dev.langchain4j.internal.Exceptions.illegalArgument;

public enum ChatMessageType {

    SYSTEM,
    USER,
    AI,
    TOOL_EXECUTION_RESULT;

    static Class<? extends ChatMessage> classOf(ChatMessageType type) {
        switch (type) {
            case SYSTEM:
                return SystemMessage.class;
            case USER:
                return UserMessage.class;
            case AI:
                return AiMessage.class;
            case TOOL_EXECUTION_RESULT:
                return ToolExecutionResultMessage.class;
            default:
                throw illegalArgument("Unknown ChatMessageType: %s", type);
        }
    }
}
