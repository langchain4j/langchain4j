package dev.langchain4j.model.moderation;

import static dev.langchain4j.internal.Utils.copyIfNotNull;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.model.output.TokenUsage;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Represents a moderation response.
 */
public class ModerationResponse {

    private final Moderation moderation;

    @Nullable
    private final TokenUsage tokenUsage;

    @Nullable
    private final Map<String, Object> metadata;

    private ModerationResponse(Builder builder) {
        this.moderation = ensureNotNull(builder.moderation, "moderation");
        this.tokenUsage = builder.tokenUsage;
        this.metadata = copyIfNotNull(builder.metadata);
    }

    /**
     * Returns the moderation result.
     *
     * @return the moderation result.
     */
    public Moderation moderation() {
        return moderation;
    }

    /**
     * Returns the token usage, if available.
     *
     * @return the token usage, or {@code null} if not available.
     */
    @Nullable
    public TokenUsage tokenUsage() {
        return tokenUsage;
    }

    /**
     * Returns the metadata, if available.
     *
     * @return the metadata, or {@code null} if not available.
     */
    @Nullable
    public Map<String, Object> metadata() {
        return metadata;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ModerationResponse that = (ModerationResponse) o;
        return Objects.equals(moderation, that.moderation)
                && Objects.equals(tokenUsage, that.tokenUsage)
                && Objects.equals(metadata, that.metadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(moderation, tokenUsage, metadata);
    }

    @Override
    public String toString() {
        return "ModerationResponse{" + "moderation="
                + moderation + ", tokenUsage="
                + tokenUsage + ", metadata="
                + metadata + '}';
    }

    /**
     * Converts this instance to a {@link Builder} with all of the same field values,
     * allowing modifications to the current object's fields.
     *
     * @return a new {@link Builder} instance initialized with the current state of this {@code ModerationResponse}.
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
     * Builder for {@link ModerationResponse}.
     */
    public static class Builder {

        private Moderation moderation;
        private TokenUsage tokenUsage;
        private Map<String, Object> metadata;

        private Builder() {}

        private Builder(ModerationResponse response) {
            this.moderation = response.moderation;
            this.tokenUsage = response.tokenUsage;
            this.metadata = response.metadata;
        }

        /**
         * Sets the moderation result.
         *
         * @param moderation the moderation result.
         * @return this builder.
         */
        public Builder moderation(Moderation moderation) {
            this.moderation = moderation;
            return this;
        }

        /**
         * Sets the token usage.
         *
         * @param tokenUsage the token usage.
         * @return this builder.
         */
        public Builder tokenUsage(TokenUsage tokenUsage) {
            this.tokenUsage = tokenUsage;
            return this;
        }

        /**
         * Sets the metadata.
         *
         * @param metadata the metadata.
         * @return this builder.
         */
        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        /**
         * Builds a new {@link ModerationResponse}.
         *
         * @return a new {@link ModerationResponse}.
         */
        public ModerationResponse build() {
            return new ModerationResponse(this);
        }
    }
}
