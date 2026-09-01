package dev.langchain4j.data.message;

import static dev.langchain4j.data.message.ChatMessageType.SYSTEM;
import static dev.langchain4j.internal.Utils.mutableCopy;
import static dev.langchain4j.internal.Utils.quoted;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

import dev.langchain4j.Experimental;
import dev.langchain4j.memory.ChatMemory;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Represents a system message, typically defined by a developer.
 * This type of message usually provides instructions regarding the AI's actions, such as its behavior or response style.
 * <br>
 * <br>
 * Optionally, system message can contain custom attributes represented by a mutable {@link Map}.
 * Attributes are not sent to the model, but they are stored in the {@link ChatMemory}.
 */
public class SystemMessage implements ChatMessage {

    private final String text;
    private final Map<String, Object> attributes;

    /**
     * Creates a new system message.
     * @param text the message text.
     */
    public SystemMessage(String text) {
        this.text = ensureNotBlank(text, "text");
        this.attributes = new HashMap<>();
    }

    /**
     * Creates a {@link SystemMessage} from a builder.
     *
     * @since 1.20.0
     */
    public SystemMessage(Builder builder) {
        this.text = ensureNotBlank(builder.text, "text");
        this.attributes = mutableCopy(builder.attributes);
    }

    /**
     * Returns the message text.
     * @return the message text.
     */
    public String text() {
        return text;
    }

    /**
     * Returns additional attributes.
     *
     * @see #attribute(String, Class)
     * @since 1.20.0
     */
    @Experimental
    public Map<String, Object> attributes() {
        return attributes;
    }

    /**
     * Returns additional attribute by it's key.
     *
     * @see #attributes()
     * @since 1.20.0
     */
    @Experimental
    public <T> T attribute(String key, Class<T> type) {
        return (T) attributes.get(key);
    }

    @Override
    public ChatMessageType type() {
        return SYSTEM;
    }

    /**
     * @since 1.20.0
     */
    public Builder toBuilder() {
        return builder().text(text).attributes(attributes);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SystemMessage that = (SystemMessage) o;
        return Objects.equals(this.text, that.text) && Objects.equals(this.attributes, that.attributes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text, attributes);
    }

    @Override
    public String toString() {
        return "SystemMessage {" + " text = " + quoted(text) + ", attributes = " + attributes + " }";
    }

    /**
     * @since 1.20.0
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * @since 1.20.0
     */
    public static class Builder {

        private String text;
        private Map<String, Object> attributes;

        public Builder text(String text) {
            this.text = text;
            return this;
        }

        public Builder attributes(Map<String, Object> attributes) {
            this.attributes = attributes;
            return this;
        }

        public SystemMessage build() {
            return new SystemMessage(this);
        }
    }

    /**
     * Creates a new system message.
     * @param text the message text.
     * @return the system message.
     */
    public static SystemMessage from(String text) {
        return new SystemMessage(text);
    }

    /**
     * Creates a new system message.
     * @param text the message text.
     * @return the system message.
     */
    public static SystemMessage systemMessage(String text) {
        return from(text);
    }

    public static Optional<SystemMessage> findFirst(List<ChatMessage> messages) {
        return messages.stream()
                .filter(message -> message instanceof SystemMessage)
                .map(message -> (SystemMessage) message)
                .findFirst();
    }

    public static Optional<SystemMessage> findLast(List<ChatMessage> messages) {
        return messages.stream()
                .filter(message -> message instanceof SystemMessage)
                .map(message -> (SystemMessage) message)
                .reduce((first, second) -> second);
    }

    public static List<SystemMessage> findAll(List<ChatMessage> messages) {
        return messages.stream()
                .filter(message -> message instanceof SystemMessage)
                .map(message -> (SystemMessage) message)
                .toList();
    }
}
