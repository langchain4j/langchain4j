package dev.langchain4j.data.message;

import java.util.Objects;

public class UserMessage extends ChatMessage {

    public UserMessage(String text) {
        super(text);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserMessage that = (UserMessage) o;
        return Objects.equals(this.text, that.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text);
    }

    @Override
    public String toString() {
        return "UserMessage {" +
                " text = \"" + text + "\"" +
                " }";
    }

    public static UserMessage from(String text) {
        return new UserMessage(text);
    }

    public static UserMessage userMessage(String text) {
        return from(text);
    }
}
