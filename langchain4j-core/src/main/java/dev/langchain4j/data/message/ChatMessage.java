package dev.langchain4j.data.message;

public interface ChatMessage {

    ChatMessageType type();

    @Deprecated
    String text();
}