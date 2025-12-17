package dev.langchain4j.model.discovery;

import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Represents metadata about an available model from a provider.
 * This class provides a unified view of model information across different providers.
 *
 * <p>Only {@code id}, {@code name}, and {@code provider} are required fields.
 * All other fields are optional and may be null depending on what information
 * the provider makes available.
 */
public class ModelDescription {

    private final String id;
    private final String name;
    private final String description;
    private final ModelProvider provider;
    private final ModelType type;
    private final Set<Capability> capabilities;
    private final ModelPricing pricing;
    private final Integer contextWindow;
    private final Integer maxOutputTokens;
    private final Instant createdAt;
    private final String owner;
    private final Boolean deprecated;
    private final Set<String> supportedLanguages;
    private final Map<String, Object> additionalMetadata;

    private ModelDescription(Builder builder) {
        this.id = Objects.requireNonNull(builder.id, "id must not be null");
        this.name = Objects.requireNonNull(builder.name, "name must not be null");
        this.provider = Objects.requireNonNull(builder.provider, "provider must not be null");
        this.description = builder.description;
        this.type = builder.type;
        this.capabilities = builder.capabilities != null
                ? Collections.unmodifiableSet(new HashSet<>(builder.capabilities))
                : Set.of();
        this.pricing = builder.pricing;
        this.contextWindow = builder.contextWindow;
        this.maxOutputTokens = builder.maxOutputTokens;
        this.createdAt = builder.createdAt;
        this.owner = builder.owner;
        this.deprecated = builder.deprecated;
        this.supportedLanguages = builder.supportedLanguages != null
                ? Collections.unmodifiableSet(new HashSet<>(builder.supportedLanguages))
                : Set.of();
        this.additionalMetadata = builder.additionalMetadata != null
                ? Collections.unmodifiableMap(new HashMap<>(builder.additionalMetadata))
                : Map.of();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Unique identifier for the model as defined by the provider.
     * For example: "gpt-4", "claude-3-opus-20240229", "llama2".
     */
    public String getId() {
        return id;
    }

    /**
     * Human-readable display name for the model.
     * May be the same as the ID for some providers.
     */
    public String getName() {
        return name;
    }

    /**
     * Optional textual description of the model's characteristics and intended use cases.
     */
    public String getDescription() {
        return description;
    }

    public ModelProvider getProvider() {
        return provider;
    }

    /**
     * Category of the model (e.g., CHAT, EMBEDDING, IMAGE_GENERATION).
     * May be null if the provider doesn't categorize models or the type is unknown.
     */
    public ModelType getType() {
        return type;
    }

    /**
     * Set of features supported by this model (e.g., JSON schema response format, tool calling).
     * Returns an empty set if capabilities are unknown, never null.
     */
    public Set<Capability> getCapabilities() {
        return capabilities;
    }

    /**
     * Cost information for using this model, typically per million tokens.
     * May be null if pricing information is not available or not applicable.
     */
    public ModelPricing getPricing() {
        return pricing;
    }

    /**
     * Maximum number of tokens that can be processed in a single request (input + output).
     * Also known as the token window or context length.
     * May be null if this information is not provided by the provider.
     */
    public Integer getContextWindow() {
        return contextWindow;
    }

    /**
     * Maximum number of tokens the model can generate in a single response.
     * This is typically smaller than the context window.
     * May be null if this information is not provided by the provider.
     */
    public Integer getMaxOutputTokens() {
        return maxOutputTokens;
    }

    /**
     * Timestamp when the model was created or released by the provider.
     * May be null if this information is not available.
     */
    public Instant getCreatedAt() {
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
        return Objects.equals(id, that.id) && Objects.equals(provider, that.provider);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, provider);
    }

    @Override
    public String toString() {
        return "ModelDescription{" + "id='"
                + id + '\'' + ", name='"
                + name + '\'' + ", provider="
                + provider + ", type="
                + type + ", contextWindow="
                + contextWindow + '}';
    }

    public static class Builder {
        private String id;
        private String name;
        private String description;
        private ModelProvider provider;
        private ModelType type;
        private Set<Capability> capabilities;
        private ModelPricing pricing;
        private Integer contextWindow;
        private Integer maxOutputTokens;
        private Instant createdAt;
        private String owner;
        private Boolean deprecated;
        private Set<String> supportedLanguages;
        private Map<String, Object> additionalMetadata;

        /**
         * Required. Unique identifier for the model as defined by the provider.
         */
        public Builder id(String id) {
            this.id = id;
            return this;
        }

        /**
         * Required. Human-readable display name for the model.
         */
        public Builder name(String name) {
            this.name = name;
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

        public Builder capabilities(Set<Capability> capabilities) {
            this.capabilities = capabilities;
            return this;
        }

        public Builder pricing(ModelPricing pricing) {
            this.pricing = pricing;
            return this;
        }

        /**
         * Maximum number of tokens (input + output) that can be processed in a single request.
         */
        public Builder contextWindow(Integer contextWindow) {
            this.contextWindow = contextWindow;
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
