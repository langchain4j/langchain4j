package dev.langchain4j.data.message;

import java.util.List;
import java.util.Objects;

import static dev.langchain4j.data.message.ChatMessageType.USER;
import static dev.langchain4j.internal.Exceptions.runtime;
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

    /**
     * Creates a {@link UserMessage} from a text.
     * @param text the text.
     */
    public UserMessage(String text) {
        this(TextContent.from(text));
    }

    /**
     * Creates a {@link UserMessage} from a name and a text.
     * @param name the name.
     * @param text the text.
     */
    public UserMessage(String name, String text) {
        this(name, TextContent.from(text));
    }

    /**
     * Creates a {@link UserMessage} from contents.
     *
     * <p>Will have a {code null} name.</p>
     *
     * @param contents the contents.
     */
    public UserMessage(Content... contents) {
        this(asList(contents));
    }

    /**
     * Creates a {@link UserMessage} from a name and contents.
     * @param name the name.
     * @param contents the contents.
     */
    public UserMessage(String name, Content... contents) {
        this(name, asList(contents));
    }

    /**
     * Creates a {@link UserMessage} from contents.
     *
     * <p>Will have a {code null} name.</p>
     *
     * @param contents the contents.
     */
    public UserMessage(List<Content> contents) {
        this.name = null;
        this.contents = unmodifiableList(ensureNotEmpty(contents, "contents"));
    }

    /**
     * Creates a {@link UserMessage} from a name and contents.
     *
     * @param name the name.
     * @param contents the contents.
     */
    public UserMessage(String name, List<Content> contents) {
        this.name = ensureNotBlank(name, "name");
        this.contents = unmodifiableList(ensureNotEmpty(contents, "contents"));
    }

    /**
     * The name of the user.
     * @return the name, or {@code null} if not set.
     */
    public String name() {
        return name;
    }

    /**
     * The contents of the message.
     * @return the contents.
     */
    public List<Content> contents() {
        return contents;
    }

    @Deprecated
    public String text() {
        if (hasSingleText()) {
            return ((TextContent) contents.get(0)).text();
        } else {
            throw runtime("Expecting single text content, but got: " + contents);
        }
    }

    /**
     * Whether this message has a single text content.
     * @return {@code true} if this message has a single text content, {@code false} otherwise.
     */
    public boolean hasSingleText() {
        return contents.size() == 1 && contents.get(0) instanceof TextContent;
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

    /**
     * Create a {@link UserMessage} from a text.
     * @param text the text.
     * @return the {@link UserMessage}.
     */
    public static UserMessage from(String text) {
        return new UserMessage(text);
    }

    /**
     * Create a {@link UserMessage} from a name and a text.
     * @param name the name.
     * @param text the text.
     * @return the {@link UserMessage}.
     */
    public static UserMessage from(String name, String text) {
        return new UserMessage(name, text);
    }

    /**
     * Create a {@link UserMessage} from contents.
     * @param contents the contents.
     * @return the {@link UserMessage}.
     */
    public static UserMessage from(Content... contents) {
        return new UserMessage(contents);
    }

    /**
     * Create a {@link UserMessage} from a name and contents.
     * @param name the name.
     * @param contents the contents.
     * @return the {@link UserMessage}.
     */
    public static UserMessage from(String name, Content... contents) {
        return new UserMessage(name, contents);
    }

    /**
     * Create a {@link UserMessage} from contents.
     * @param contents the contents.
     * @return the {@link UserMessage}.
     */
    public static UserMessage from(List<Content> contents) {
        return new UserMessage(contents);
    }

    /**
     * Create a {@link UserMessage} from a name and contents.
     * @param name the name.
     * @param contents the contents.
     * @return the {@link UserMessage}.
     */
    public static UserMessage from(String name, List<Content> contents) {
        return new UserMessage(name, contents);
    }

    /**
     * Create a {@link UserMessage} from a text.
     * @param text the text.
     * @return the {@link UserMessage}.
     */
    public static UserMessage userMessage(String text) {
        return from(text);
    }

    /**
     * Create a {@link UserMessage} from a name and a text.
     * @param name the name.
     * @param text the text.
     * @return the {@link UserMessage}.
     */
    public static UserMessage userMessage(String name, String text) {
        return from(name, text);
    }

    /**
     * Create a {@link UserMessage} from contents.
     * @param contents the contents.
     * @return the {@link UserMessage}.
     */
    public static UserMessage userMessage(Content... contents) {
        return from(contents);
    }

    /**
     * Create a {@link UserMessage} from a name and contents.
     * @param name the name.
     * @param contents the contents.
     * @return the {@link UserMessage}.
     */
    public static UserMessage userMessage(String name, Content... contents) {
        return from(name, contents);
    }

    /**
     * Create a {@link UserMessage} from contents.
     * @param contents the contents.
     * @return the {@link UserMessage}.
     */
    public static UserMessage userMessage(List<Content> contents) {
        return from(contents);
    }

    /**
     * Create a {@link UserMessage} from a name and contents.
     * @param name the name.
     * @param contents the contents.
     * @return the {@link UserMessage}.
     */
    public static UserMessage userMessage(String name, List<Content> contents) {
        return from(name, contents);
    }
}
