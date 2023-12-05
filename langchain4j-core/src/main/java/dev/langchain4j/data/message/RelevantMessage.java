package dev.langchain4j.data.message;

import java.util.Objects;

import static dev.langchain4j.data.message.ChatMessageType.Relevant;
import static dev.langchain4j.internal.Utils.quoted;

public class RelevantMessage extends ChatMessage {

    private final String name;

    public RelevantMessage(String text) {
        this(null, text);
    }

    public RelevantMessage(String name, String text) {
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
        RelevantMessage that = (RelevantMessage) o;
        return Objects.equals(this.name, that.name)
                && Objects.equals(this.text, that.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, text);
    }

    @Override
    public String toString() {
        return "RelevantMessage {" +
                " name = " + quoted(name) +
                " text = " + quoted(text) +
                " }";
    }

    public static RelevantMessage from(String text) {
        return new RelevantMessage(text);
    }

    public static RelevantMessage from(String name, String text) {
        return new RelevantMessage(name, text);
    }

    public static RelevantMessage relevantMessage(String text) {
        return from(text);
    }

    public static RelevantMessage relevantMessage(String name, String text) {
        return from(name, text);
    }
}
