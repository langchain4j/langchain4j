package dev.langchain4j.data.message;

import java.util.Objects;

import static dev.langchain4j.data.message.ChatMessageType.USER;
import static dev.langchain4j.internal.Utils.quoted;

/**
 * Represents a message from a user, typically an end user of the application.
 */
public class UserMessage extends ChatMessage {

    private final String name;

    public UserMessage(String text) {
        this(null, text);
    }

    public UserMessage(String name, String text) {
        super(text);
        this.name = name;
    }

    public String name() {
        return name;
    }

    @Override
    public ChatMessageType type() {
        return USER;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserMessage that = (UserMessage) o;
        return Objects.equals(this.name, that.name)
                && Objects.equals(this.text, that.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, text);
    }

    @Override
    public String toString() {
        return "UserMessage {" +
                " name = " + quoted(name) +
                " text = " + quoted(text) +
                " }";
    }

    public static UserMessage from(String text) {
        return new UserMessage(text);
    }

    public static UserMessage from(String name, String text) {
        return new UserMessage(name, text);
    }

    public static UserMessage userMessage(String text) {
        return from(text);
    }

    public static UserMessage userMessage(String name, String text) {
        return from(name, text);
    }
}
