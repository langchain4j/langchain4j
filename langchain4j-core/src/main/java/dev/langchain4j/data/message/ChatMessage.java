package dev.langchain4j.data.message;

public abstract class ChatMessage {

    protected final String text;

    ChatMessage(String text) {
        this.text = text;
    }

    public String text() {
        return text;
    }

    public abstract ChatMessageType type();
}