package dev.langchain4j.model.moderation;

import static dev.langchain4j.internal.Utils.copyIfNotNull;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;

import java.util.List;
import java.util.Objects;

/**
 * Represents a moderation request.
 */
public class ModerationRequest {

    private final List<String> messages;

    private ModerationRequest(Builder builder) {
        this.messages = copyIfNotNull(builder.messages);
    }

    /**
     * Returns the list of text messages to moderate.
     *
     * @return the list of text messages to moderate.
     */
    public List<String> messages() {
        return messages;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ModerationRequest that = (ModerationRequest) o;
        return Objects.equals(messages, that.messages);
    }

    @Override
    public int hashCode() {
        return Objects.hash(messages);
    }

    @Override
    public String toString() {
        return "ModerationRequest{" + "messages=" + messages + '}';
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

        private List<String> messages;

        private Builder() {}

        private Builder(ModerationRequest request) {
            this.messages = request.messages;
        }

        /**
         * Sets the list of text messages to moderate.
         *
         * @param messages the list of text messages to moderate.
         * @return this builder.
         */
        public Builder messages(List<String> messages) {
            this.messages = messages;
            return this;
        }

        /**
         * Builds a new {@link ModerationRequest}.
         *
         * @return a new {@link ModerationRequest}.
         * @throws IllegalArgumentException if messages is null or empty.
         */
        public ModerationRequest build() {
            ensureNotEmpty(messages, "messages");
            return new ModerationRequest(this);
        }
    }
}
