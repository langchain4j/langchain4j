package dev.langchain4j.data.message;

import java.util.Objects;

import static dev.langchain4j.data.message.ContentType.TEXT;
import static dev.langchain4j.internal.Utils.quoted;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

public class TextContent implements Content {

    private final String text;

    public TextContent(String text) {
        this.text = ensureNotBlank(text, "text");
    }

    public String text() {
        return text;
    }

    @Override
    public ContentType type() {
        return TEXT;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TextContent that = (TextContent) o;
        return Objects.equals(this.text, that.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text);
    }

    @Override
    public String toString() {
        return "TextContent {" +
                " text = " + quoted(text) +
                " }";
    }

    public static TextContent from(String text) {
        return new TextContent(text);
    }
}
