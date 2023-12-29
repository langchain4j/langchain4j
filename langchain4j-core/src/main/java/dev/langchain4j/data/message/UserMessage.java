package dev.langchain4j.data.message;

import java.util.List;
import java.util.Objects;

import static dev.langchain4j.data.message.ChatMessageType.USER;
import static dev.langchain4j.data.message.ContentType.TEXT;
import static dev.langchain4j.internal.Utils.quoted;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;

/**
 * Represents a message from a user, typically an end user of the application.
 */
public class UserMessage implements ChatMessage {

    private final String name;
    private final List<Content> contents;

    public UserMessage(String text) {
        this(TextContent.from(text));
    }

    public UserMessage(String name, String text) {
        this(name, TextContent.from(text));
    }

    public UserMessage(Content... contents) {
        this(asList(contents));
    }

    public UserMessage(String name, Content... contents) {
        this(name, asList(contents));
    }

    public UserMessage(List<Content> contents) {
        this.name = null;
        this.contents = unmodifiableList(ensureNotEmpty(contents, "contents"));
    }

    public UserMessage(String name, List<Content> contents) {
        this.name = ensureNotBlank(name, "name");
        this.contents = unmodifiableList(ensureNotEmpty(contents, "contents"));
    }

    public String name() {
        return name;
    }

    public List<Content> contents() {
        return contents;
    }

    @Deprecated
    public String text() {
        if (contents.size() == 1 && contents.get(0).type() == TEXT) {
            return ((TextContent) contents.get(0)).text();
        }
        throw new RuntimeException("Expecting single text content, but got: " + contents);
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
                && Objects.equals(this.contents, that.contents);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, contents);
    }

    @Override
    public String toString() {
        return "UserMessage {" +
                " name = " + quoted(name) +
                " contents = " + contents +
                " }";
    }

    public static UserMessage from(String text) {
        return new UserMessage(text);
    }

    public static UserMessage from(String name, String text) {
        return new UserMessage(name, text);
    }

    public static UserMessage from(Content... contents) {
        return new UserMessage(contents);
    }

    public static UserMessage from(String name, Content... contents) {
        return new UserMessage(name, contents);
    }

    public static UserMessage from(List<Content> contents) {
        return new UserMessage(contents);
    }

    public static UserMessage from(String name, List<Content> contents) {
        return new UserMessage(name, contents);
    }

    public static UserMessage userMessage(String text) {
        return from(text);
    }

    public static UserMessage userMessage(String name, String text) {
        return from(name, text);
    }

    public static UserMessage userMessage(Content... contents) {
        return from(contents);
    }

    public static UserMessage userMessage(String name, Content... contents) {
        return from(name, contents);
    }

    public static UserMessage userMessage(List<Content> contents) {
        return from(contents);
    }

    public static UserMessage userMessage(String name, List<Content> contents) {
        return from(name, contents);
    }
}
