package dev.langchain4j.model.ollama;

import dev.langchain4j.data.message.ChatMessageType;

public enum Role {
    SYSTEM,
    USER,
    ASSISTANT;

    public static Role fromChatMessageType(ChatMessageType chatMessageType) {
        switch (chatMessageType) {
            case SYSTEM:
                return SYSTEM;
            case USER:
                return USER;
            case AI:
                return Role.ASSISTANT;
            default:
                throw new IllegalArgumentException("Unknown ChatMessageType: " + chatMessageType);
        }
    }
}