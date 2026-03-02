package dev.langchain4j.model.moderation;

import static dev.langchain4j.internal.Utils.copyIfNotNull;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Represents a moderation request.
 */
public class ModerationRequest {

    private final List<String> texts;

    @Nullable
    private final String modelName;

    private ModerationRequest(Builder builder) {
        this.texts = copyIfNotNull(builder.texts);
        this.modelName = builder.modelName;
    }

    /**
     * Returns the list of texts to moderate.
     *
     * @return the list of texts to moderate.
     */
    public List<String> texts() {
        return texts;
    }

    /**
     * Returns the model name to use for this request.
     * When set, this overrides the model name configured when the {@link ModerationModel} was created.
     *
     * @return the model name, or {@code null} if not specified.
     */
    @Nullable
    public String modelName() {
        return modelName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ModerationRequest that = (ModerationRequest) o;
        return Objects.equals(texts, that.texts) && Objects.equals(modelName, that.modelName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(texts, modelName);
    }

    @Override
    public String toString() {
        return "ModerationRequest{" + "texts=" + texts + ", modelName='" + modelName + '\'' + '}';
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

        private List<String> texts;
        private String modelName;

        private Builder() {}

        private Builder(ModerationRequest request) {
            this.texts = request.texts;
            this.modelName = request.modelName;
        }

        /**
         * Sets the list of texts to moderate.
         *
         * @param texts the list of texts to moderate.
         * @return this builder.
         */
        public Builder texts(List<String> texts) {
            this.texts = texts;
            return this;
        }

        /**
         * Sets the model name to use for this request.
         * When set, this overrides the model name configured when the {@link ModerationModel} was created.
         *
         * @param modelName the model name.
         * @return this builder.
         */
        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        /**
         * Builds a new {@link ModerationRequest}.
         *
         * @return a new {@link ModerationRequest}.
         * @throws IllegalArgumentException if texts is null or empty.
         */
        public ModerationRequest build() {
            ensureNotEmpty(texts, "texts");
            return new ModerationRequest(this);
        }
    }
}
