package dev.langchain4j.model.moderation;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Represents a moderation response.
 */
public class ModerationResponse {

    private final Moderation moderation;
    private final Map<String, Object> metadata;

    @Nullable
    private final ModerationResponseMetadata typedMetadata;

    private ModerationResponse(Builder builder) {
        this.moderation = ensureNotNull(builder.moderation, "moderation");
        this.metadata = copy(builder.metadata);
        this.typedMetadata = builder.typedMetadata;
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
     * Returns the metadata.
     *
     * @return the metadata.
     */
    public Map<String, Object> metadata() {
        return metadata;
    }

    /**
     * Returns typed provider-specific metadata.
     *
     * @return the typed metadata, or {@code null} if unavailable.
     */
    @Nullable
    public ModerationResponseMetadata typedMetadata() {
        return typedMetadata;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ModerationResponse that = (ModerationResponse) o;
        return Objects.equals(moderation, that.moderation) && Objects.equals(metadata, that.metadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(moderation, metadata);
    }

    @Override
    public String toString() {
        return "ModerationResponse{"
                + "moderation=" + moderation
                + ", metadata=" + metadata
                + ", typedMetadata=" + typedMetadata
                + '}';
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

        @Nullable
        private Map<String, Object> metadata;

        @Nullable
        private ModerationResponseMetadata typedMetadata;

        private Builder() {}

        private Builder(ModerationResponse response) {
            this.moderation = response.moderation;
            this.metadata = response.metadata;
            this.typedMetadata = response.typedMetadata;
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
         * Sets the metadata.
         *
         * @param metadata the metadata, or {@code null} if unavailable.
         * @return this builder.
         */
        public Builder metadata(@Nullable Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        /**
         * Sets typed provider-specific metadata.
         *
         * @param typedMetadata the typed metadata, or {@code null} if unavailable.
         * @return this builder.
         */
        public Builder typedMetadata(@Nullable ModerationResponseMetadata typedMetadata) {
            this.typedMetadata = typedMetadata;
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
