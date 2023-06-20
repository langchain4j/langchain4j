package dev.langchain4j.data.message;

import java.util.Objects;

public class AiMessage extends ChatMessage {

    public AiMessage(String text) {
        super(text);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AiMessage that = (AiMessage) o;
        return Objects.equals(this.text, that.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text);
    }

    @Override
    public String toString() {
        return "AiMessage {" +
                " text = \"" + text + "\"" +
                " }";
    }

    public static AiMessage from(String text) {
        return new AiMessage(text);
    }

    public static AiMessage aiMessage(String text) {
        return from(text);
    }
}
