package dev.langchain4j.model.catalog;

import static dev.langchain4j.internal.Utils.*;
import static dev.langchain4j.internal.ValidationUtils.*;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import dev.langchain4j.model.ModelProvider;

/**
 * Represents metadata about an available model from a provider.
 * This class provides a unified view of model information across different providers.
 *
 * <p>Only {@code id}, {@code name}, and {@code provider} are required fields.
 * All other fields are optional and may be null depending on what information
 * the provider makes available.
 */
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
    private final Boolean deprecated;
    private final Set<String> supportedLanguages;
    private final Map<String, Object> additionalMetadata;

    private ModelDescription(Builder builder) {
        this.name = ensureNotNull(builder.name, "id");
        this.displayName = ensureNotNull(builder.displayName, "name");
        this.provider = ensureNotNull(builder.provider, "provider");
        this.description = builder.description;
        this.type = builder.type;
        this.maxInputTokens = builder.maxInputTokens;
        this.maxOutputTokens = builder.maxOutputTokens;
        this.createdAt = builder.createdAt;
        this.owner = builder.owner;
        this.deprecated = builder.deprecated;
        this.supportedLanguages = copy(builder.supportedLanguages);
        this.additionalMetadata = copy(builder.additionalMetadata);
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
     * May be the same as the ID for some providers.
     */
    public String displayName() {
        return displayName;
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
     * Category of the model (e.g., CHAT, EMBEDDING, IMAGE_GENERATION).
     * May be null if the provider doesn't categorize models or the type is unknown.
     */
    public ModelType type() {
        return type;
    }

    /**
     * Maximum number of input tokens the model can accept in a single request.
     * This represents the limit on the size of the prompt/input that can be sent to the model.
     * May be null if this information is not provided by the provider.
     *
     * <p>Note: For some models, this may be the same as the context window if the provider
     * doesn't distinguish between input and output token limits separately.
     */
    public Integer maxInputTokens() {
        return maxInputTokens;
    }

    /**
     * Maximum number of tokens the model can generate in a single response.
     * This is typically smaller than the context window.
     * May be null if this information is not provided by the provider.
     */
    public Integer maxOutputTokens() {
        return maxOutputTokens;
    }

    /**
     * Timestamp when the model was created or released by the provider.
     * May be null if this information is not available.
     */
    public Instant createdAt() {
        return createdAt;
    }

    /**
     * Organization or entity that created or owns the model.
     * For example: "openai", "anthropic", "meta".
     * May be null if this information is not provided.
     */
    public String getOwner() {
        return owner;
    }

    /**
     * Indicates whether this model has been deprecated by the provider.
     * A value of true means the model may be removed in the future.
     * Null indicates deprecation status is unknown.
     */
    public Boolean isDeprecated() {
        return deprecated;
    }

    /**
     * Set of natural language codes (e.g., "en", "fr", "ja") that the model supports.
     * Returns an empty set if language information is not available, never null.
     */
    public Set<String> getSupportedLanguages() {
        return supportedLanguages;
    }

    /**
     * Provider-specific metadata that doesn't fit into standard fields.
     * Allows providers to include custom information about their models.
     * Returns an empty map if no additional metadata is available, never null.
     */
    public Map<String, Object> getAdditionalMetadata() {
        return additionalMetadata;
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
        return "ModelDescription{" + 
        		"name='" + name + '\'' + 
        		", displayName='" + displayName + '\'' + 
        		", provider=" + provider + 
        		", type=" + type + 
        		", maxInputTokens=" + maxInputTokens + 
                ", maxOutputTokens=" + maxOutputTokens + '}';
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
        private Boolean deprecated;
        private Set<String> supportedLanguages;
        private Map<String, Object> additionalMetadata;

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
         * Whether this model has been deprecated and may be removed in the future.
         */
        public Builder deprecated(Boolean deprecated) {
            this.deprecated = deprecated;
            return this;
        }

        /**
         * Natural language codes (e.g., "en", "fr") that the model supports.
         */
        public Builder supportedLanguages(Set<String> supportedLanguages) {
            this.supportedLanguages = supportedLanguages;
            return this;
        }

        /**
         * Provider-specific metadata that doesn't fit into standard fields.
         */
        public Builder additionalMetadata(Map<String, Object> additionalMetadata) {
            this.additionalMetadata = additionalMetadata;
            return this;
        }

        /**
         * Constructs a ModelDescription instance.
         *
         * @throws NullPointerException if id, name, or provider is null
         */
        public ModelDescription build() {
            return new ModelDescription(this);
        }
    }
}
