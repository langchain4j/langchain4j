package dev.langchain4j.data.message;

import java.util.Objects;

import static dev.langchain4j.data.message.ChatMessageType.Relevant;
import static dev.langchain4j.internal.Utils.quoted;

public class RelevantAiMessage extends ChatMessage {

    private final String name;

    public RelevantAiMessage(String text) {
        this(null, text);
    }

    public RelevantAiMessage(String name, String text) {
        super(text);
        this.name = name;
    }

    public String name() {
        return name;
    }

    @Override
    public ChatMessageType type() {
        return Relevant;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RelevantAiMessage that = (RelevantAiMessage) o;
        return Objects.equals(this.name, that.name)
                && Objects.equals(this.text, that.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, text);
    }

    @Override
    public String toString() {
        return "RelevantAiMessage {" +
                " name = " + quoted(name) +
                " text = " + quoted(text) +
                " }";
    }

    public static RelevantAiMessage from(String text) {
        return new RelevantAiMessage(text);
    }

    public static RelevantAiMessage from(String name, String text) {
        return new RelevantAiMessage(name, text);
    }

    public static RelevantAiMessage relevantAiMessage(String text) {
        return from(text);
    }

    public static RelevantAiMessage relevantAiMessage(String name, String text) {
        return from(name, text);
    }
}
