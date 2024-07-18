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
 * <br>
 * Depending on the supported modalities (text, image, audio, video, etc.) of the model,
 * user messages can contain either a single text (a {@code String}) or multiple {@link Content}s,
 * which can be either {@link TextContent}, {@link ImageContent}, {@link AudioContent},
 * {@link VideoContent}, or {@link PdfFileContent}.
 * <br>
 * Optionally, user message can contain a {@link #name} of the user.
 * Be aware that not all models support names in {@code UserMessage}.
 */
public class UserMessage implements ChatMessage {

    private final String name;
    private final List<Content> contents;

    /**
     * Creates a {@link UserMessage} from a text.
     *
     * @param text the text.
     */
    public UserMessage(String text) {
        this(TextContent.from(text));
    }

    /**
     * Creates a {@link UserMessage} from a name and a text.
     *
     * @param name the name.
     * @param text the text.
     */
    public UserMessage(String name, String text) {
        this(name, TextContent.from(text));
    }

    /**
     * Creates a {@link UserMessage} from one or multiple {@link Content}s.
     * {@link Content} can be either {@link TextContent} or {@link ImageContent}.
     * <br>
     * Will have a {@code null} name.
     *
     * @param contents the contents.
     */
    public UserMessage(Content... contents) {
        this(asList(contents));
    }

    /**
     * Creates a {@link UserMessage} from a name and one or multiple {@link Content}s.
     * {@link Content} can be either {@link TextContent} or {@link ImageContent}.
     *
     * @param name     the name.
     * @param contents the contents.
     */
    public UserMessage(String name, Content... contents) {
        this(name, asList(contents));
    }

    /**
     * Creates a {@link UserMessage} from a list of {@link Content}s.
     * {@link Content} can be either {@link TextContent} or {@link ImageContent}.
     * <br>
     * Will have a {@code null} name.
     *
     * @param contents the contents.
     */
    public UserMessage(List<Content> contents) {
        this.name = null;
        this.contents = unmodifiableList(ensureNotEmpty(contents, "contents"));
    }

    /**
     * Creates a {@link UserMessage} from a name and a list of {@link Content}s.
     * {@link Content} can be either {@link TextContent} or {@link ImageContent}.
     *
     * @param name     the name.
     * @param contents the contents.
     */
    public UserMessage(String name, List<Content> contents) {
        this.name = ensureNotBlank(name, "name");
        this.contents = unmodifiableList(ensureNotEmpty(contents, "contents"));
    }

    /**
     * The name of the user.
     *
     * @return the name, or {@code null} if not set.
     */
    public String name() {
        return name;
    }

    /**
     * The contents of the message. {@link Content} can be either {@link TextContent} or {@link ImageContent}.
     *
     * @return the contents.
     */
    public List<Content> contents() {
        return contents;
    }

    /**
     * Returns text from a single {@link TextContent}.
     * Use this accessor only if you are certain that the message contains only a single text.
     * If the message contains multiple {@link Content}s, or if the only {@link Content} is not a {@link TextContent},
     * a {@link RuntimeException} is thrown.
     *
     * @return a single text.
     * @see #hasSingleText()
     */
    public String singleText() {
        if (hasSingleText()) {
            return ((TextContent) contents.get(0)).text();
        } else {
            throw runtime("Expecting single text content, but got: " + contents);
        }
    }

    /**
     * Whether this message contains a single {@link TextContent}.
     *
     * @return {@code true} if this message contains a single {@link TextContent}, {@code false} otherwise.
     */
    public boolean hasSingleText() {
        return contents.size() == 1 && contents.get(0) instanceof TextContent;
    }

    /**
     * {@link UserMessage} can contain not just a single {@code String text}, but also multiple {@link Content}s,
     * which can be either {@link TextContent} or {@link ImageContent}.
     * Therefore, this method is deprecated. Please use {@link #singleText()} if you only expect a single text,
     * or use {@link #contents()} otherwise.
     *
     * @deprecated Use {@link #singleText()} or {@link #contents()} instead.
     */
    @Deprecated
    public String text() {
        return singleText();
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
     *
     * @param text the text.
     * @return the {@link UserMessage}.
     */
    public static UserMessage from(String text) {
        return new UserMessage(text);
    }

    /**
     * Create a {@link UserMessage} from a name and a text.
     *
     * @param name the name.
     * @param text the text.
     * @return the {@link UserMessage}.
     */
    public static UserMessage from(String name, String text) {
        return new UserMessage(name, text);
    }

    /**
     * Create a {@link UserMessage} from contents.
     *
     * @param contents the contents.
     * @return the {@link UserMessage}.
     */
    public static UserMessage from(Content... contents) {
        return new UserMessage(contents);
    }

    /**
     * Create a {@link UserMessage} from a name and contents.
     *
     * @param name     the name.
     * @param contents the contents.
     * @return the {@link UserMessage}.
     */
    public static UserMessage from(String name, Content... contents) {
        return new UserMessage(name, contents);
    }

    /**
     * Create a {@link UserMessage} from contents.
     *
     * @param contents the contents.
     * @return the {@link UserMessage}.
     */
    public static UserMessage from(List<Content> contents) {
        return new UserMessage(contents);
    }

    /**
     * Create a {@link UserMessage} from a name and contents.
     *
     * @param name     the name.
     * @param contents the contents.
     * @return the {@link UserMessage}.
     */
    public static UserMessage from(String name, List<Content> contents) {
        return new UserMessage(name, contents);
    }

    /**
     * Create a {@link UserMessage} from a text.
     *
     * @param text the text.
     * @return the {@link UserMessage}.
     */
    public static UserMessage userMessage(String text) {
        return from(text);
    }

    /**
     * Create a {@link UserMessage} from a name and a text.
     *
     * @param name the name.
     * @param text the text.
     * @return the {@link UserMessage}.
     */
    public static UserMessage userMessage(String name, String text) {
        return from(name, text);
    }

    /**
     * Create a {@link UserMessage} from contents.
     *
     * @param contents the contents.
     * @return the {@link UserMessage}.
     */
    public static UserMessage userMessage(Content... contents) {
        return from(contents);
    }

    /**
     * Create a {@link UserMessage} from a name and contents.
     *
     * @param name     the name.
     * @param contents the contents.
     * @return the {@link UserMessage}.
     */
    public static UserMessage userMessage(String name, Content... contents) {
        return from(name, contents);
    }

    /**
     * Create a {@link UserMessage} from contents.
     *
     * @param contents the contents.
     * @return the {@link UserMessage}.
     */
    public static UserMessage userMessage(List<Content> contents) {
        return from(contents);
    }

    /**
     * Create a {@link UserMessage} from a name and contents.
     *
     * @param name     the name.
     * @param contents the contents.
     * @return the {@link UserMessage}.
     */
    public static UserMessage userMessage(String name, List<Content> contents) {
        return from(name, contents);
    }
}
