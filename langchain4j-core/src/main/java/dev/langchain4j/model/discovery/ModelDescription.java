package dev.langchain4j.model.discovery;

import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import java.time.Instant;
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
        this.capabilities = builder.capabilities != null ? Set.copyOf(builder.capabilities) : Set.of();
        this.pricing = builder.pricing;
        this.contextWindow = builder.contextWindow;
        this.maxOutputTokens = builder.maxOutputTokens;
        this.createdAt = builder.createdAt;
        this.owner = builder.owner;
        this.deprecated = builder.deprecated;
        this.supportedLanguages = builder.supportedLanguages != null ? Set.copyOf(builder.supportedLanguages) : Set.of();
        this.additionalMetadata = builder.additionalMetadata != null ? Map.copyOf(builder.additionalMetadata) : Map.of();
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public ModelProvider getProvider() {
        return provider;
    }

    public ModelType getType() {
        return type;
    }

    public Set<Capability> getCapabilities() {
        return capabilities;
    }

    public ModelPricing getPricing() {
        return pricing;
    }

    public Integer getContextWindow() {
        return contextWindow;
    }

    public Integer getMaxOutputTokens() {
        return maxOutputTokens;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getOwner() {
        return owner;
    }

    public Boolean isDeprecated() {
        return deprecated;
    }

    public Set<String> getSupportedLanguages() {
        return supportedLanguages;
    }

    public Map<String, Object> getAdditionalMetadata() {
        return additionalMetadata;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ModelDescription)) return false;
        ModelDescription that = (ModelDescription) o;
        return Objects.equals(id, that.id) &&
               Objects.equals(provider, that.provider);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, provider);
    }

    @Override
    public String toString() {
        return "ModelDescription{" +
               "id='" + id + '\'' +
               ", name='" + name + '\'' +
               ", provider=" + provider +
               ", type=" + type +
               ", contextWindow=" + contextWindow +
               '}';
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

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

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

        public Builder contextWindow(Integer contextWindow) {
            this.contextWindow = contextWindow;
            return this;
        }

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

        public Builder deprecated(Boolean deprecated) {
            this.deprecated = deprecated;
            return this;
        }

        public Builder supportedLanguages(Set<String> supportedLanguages) {
            this.supportedLanguages = supportedLanguages;
            return this;
        }

        public Builder additionalMetadata(Map<String, Object> additionalMetadata) {
            this.additionalMetadata = additionalMetadata;
            return this;
        }

        public ModelDescription build() {
            return new ModelDescription(this);
        }
    }
}
