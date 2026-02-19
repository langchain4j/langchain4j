package dev.langchain4j.model.moderation;

import static dev.langchain4j.internal.Utils.copyIfNotNull;

import dev.langchain4j.data.message.ChatMessage;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Represents a moderation request.
 */
public class ModerationRequest {

    @Nullable
    private final String text;

    @Nullable
    private final List<ChatMessage> messages;

    private ModerationRequest(Builder builder) {
        this.text = builder.text;
        this.messages = copyIfNotNull(builder.messages);
    }

    /**
     * Returns the text to moderate.
     *
     * @return the text to moderate, or {@code null} if not set.
     */
    @Nullable
    public String text() {
        return text;
    }

    /**
     * Returns the list of chat messages to moderate.
     *
     * @return the list of chat messages to moderate, or {@code null} if not set.
     */
    @Nullable
    public List<ChatMessage> messages() {
        return messages;
    }

    /**
     * Returns {@code true} if text is set.
     *
     * @return {@code true} if text is set.
     */
    public boolean hasText() {
        return text != null;
    }

    /**
     * Returns {@code true} if messages are set.
     *
     * @return {@code true} if messages are set.
     */
    public boolean hasMessages() {
        return messages != null && !messages.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ModerationRequest that = (ModerationRequest) o;
        return Objects.equals(text, that.text) && Objects.equals(messages, that.messages);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text, messages);
    }

    @Override
    public String toString() {
        return "ModerationRequest{" + "text=" + text + ", messages=" + messages + '}';
    }

    /**
     * Converts this instance to a {@link Builder} with all of the same field values,
     * allowing modifications to the current object's fields.
     *
     * @return a new {@link Builder} instance initialized with the current state of this {@code ModerationRequest}.
     */
    public Builder toBuilder() {
        return new Builder(this);
    }

    /**
     * Creates a new builder.
     *
     * @return a new builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link ModerationRequest}.
     */
    public static class Builder {

        private String text;
        private List<ChatMessage> messages;

        private Builder() {}

        private Builder(ModerationRequest request) {
            this.text = request.text;
            this.messages = request.messages;
        }

        /**
         * Sets the text to moderate.
         *
         * @param text the text to moderate.
         * @return this builder.
         */
        public Builder text(String text) {
            this.text = text;
            return this;
        }

        /**
         * Sets the list of chat messages to moderate.
         *
         * @param messages the list of chat messages to moderate.
         * @return this builder.
         */
        public Builder messages(List<ChatMessage> messages) {
            this.messages = messages;
            return this;
        }

        /**
         * Builds a new {@link ModerationRequest}.
         *
         * @return a new {@link ModerationRequest}.
         * @throws IllegalArgumentException if neither text nor messages are set.
         */
        public ModerationRequest build() {
            boolean hasText = text != null;
            boolean hasMessages = messages != null && !messages.isEmpty();
            if (!hasText && !hasMessages) {
                throw new IllegalArgumentException("Either text or messages must be set");
            }
            if (hasText && hasMessages) {
                throw new IllegalArgumentException("Only one of text or messages can be set, not both");
            }
            return new ModerationRequest(this);
        }
    }
}
