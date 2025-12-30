package dev.langchain4j.model.catalog;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.ModelProvider;

import java.time.Instant;
import java.util.Objects;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * Represents metadata about an available model from a provider.
 * This class provides a unified view of model information across different providers.
 *
 * <p>Only {@code name} and {@code provider} are required fields.
 * All other fields are optional and may be <code>null</code> depending on what information
 * the provider makes available.
 *
 * @since 1.10.0
 */
@Experimental
public class ModelDescription {

    private final String name;
    private final String displayName;
    private final String description;
    private final ModelProvider provider;
    private final ModelType type;
    private final Integer maxInputTokens;
    private final Integer maxOutputTokens;
    private final Instant createdAt;
    private final String owner;

    private ModelDescription(Builder builder) {
        this.name = ensureNotNull(builder.name, "name");
        this.displayName = builder.displayName;
        this.provider = ensureNotNull(builder.provider, "provider");
        this.description = builder.description;
        this.type = builder.type;
        this.maxInputTokens = builder.maxInputTokens;
        this.maxOutputTokens = builder.maxOutputTokens;
        this.createdAt = builder.createdAt;
        this.owner = builder.owner;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Unique identifier for the model as defined by the provider.
     * For example: "gpt-4", "claude-3-opus-20240229", "llama2".
     */
    public String name() {
        return name;
    }

    /**
     * Human-readable display name for the model.
     * If absent, {@link #name()} will be returned.
     */
    public String displayName() {
        return displayName == null ? name : displayName;
    }

    /**
     * Optional textual description of the model's characteristics and intended use cases.
     */
    public String description() {
        return description;
    }

    public ModelProvider provider() {
        return provider;
    }

    /**
     * Type of the model (e.g., {@link ModelType#CHAT}, {@link ModelType#EMBEDDING}, {@link ModelType#IMAGE_GENERATION}).
     * May be <code>null</code> if the provider doesn't categorize models or the type is unknown.
     */
    public ModelType type() {
        return type;
    }

    /**
     * Maximum number of input tokens the model can accept in a single request.
     * This represents the limit on the size of the prompt/input that can be sent to the model.
     * May be <code>null</code> if this information is not provided by the provider.
     */
    public Integer maxInputTokens() {
        return maxInputTokens;
    }

    /**
     * Maximum number of tokens the model can generate in a single response.
     * This is typically smaller than the context window.
     * May be <code>null</code> if this information is not provided by the provider.
     */
    public Integer maxOutputTokens() {
        return maxOutputTokens;
    }

    /**
     * Timestamp when the model was created or released by the provider.
     * May be <code>null</code> if this information is not available.
     */
    public Instant createdAt() {
        return createdAt;
    }

    /**
     * Organization or entity that created or owns the model.
     * For example: "openai", "anthropic", "meta".
     * May be <code>null</code> if this information is not provided.
     */
    public String owner() {
        return owner;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ModelDescription)) return false;
        ModelDescription that = (ModelDescription) o;
        return Objects.equals(name, that.name) && Objects.equals(provider, that.provider);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, provider);
    }

    @Override
    public String toString() {
        return "ModelDescription{" + "name='"
                + name + '\'' + ", displayName='"
                + displayName + '\'' + ", description='"
                + description + '\'' + ", provider="
                + provider + ", type="
                + type + ", maxInputTokens="
                + maxInputTokens + ", maxOutputTokens="
                + maxOutputTokens + ", createdAt="
                + createdAt + ", owner='"
                + owner + '\'' + '}';
    }

    public static class Builder {

        private String name;
        private String displayName;
        private String description;
        private ModelProvider provider;
        private ModelType type;
        private Integer maxInputTokens;
        private Integer maxOutputTokens;
        private Instant createdAt;
        private String owner;

        /**
         * Required. Unique identifier for the model as defined by the provider.
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * Required. Human-readable display name for the model.
         */
        public Builder displayName(String name) {
            this.displayName = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        /**
         * Required. The provider that offers this model.
         */
        public Builder provider(ModelProvider provider) {
            this.provider = provider;
            return this;
        }

        public Builder type(ModelType type) {
            this.type = type;
            return this;
        }

        /**
         * Maximum number of input tokens the model can accept in a single request.
         */
        public Builder maxInputTokens(Integer maxInputTokens) {
            this.maxInputTokens = maxInputTokens;
            return this;
        }

        /**
         * Maximum number of tokens the model can generate in a single response.
         */
        public Builder maxOutputTokens(Integer maxOutputTokens) {
            this.maxOutputTokens = maxOutputTokens;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder owner(String owner) {
            this.owner = owner;
            return this;
        }

        /**
         * Constructs a ModelDescription instance.
         *
         * @throws NullPointerException if id, name, or provider is <code>null</code>
         */
        public ModelDescription build() {
            return new ModelDescription(this);
        }
    }
}
