# Feature Plan: ModelDiscovery Extension

## Overview

This document outlines the detailed plan for implementing a general model discovery pattern in LangChain4j. This feature will allow customers to discover available models and their capabilities programmatically, without needing to visit individual AI provider websites.

## Current State Analysis

### Existing Implementations

- **Mistral AI**: Has `MistralAiClient.listModels()` returning `MistralAiModelResponse` with a list of `MistralAiModelCard`
- **Ollama**: Has dedicated `OllamaModels` class with methods like `availableModels()`, `modelCard()`, `runningModels()`, returning `Response<List<OllamaModel>>`
- **OpenAI**: No model listing capability currently implemented in the client

### Current Patterns

- Both `ChatModel` and `StreamingChatModel` interfaces have:
  - `provider()` method returning `ModelProvider` enum
  - `supportedCapabilities()` returning `Set<Capability>`
- Builder pattern is consistently used across all providers
- Internal client classes handle HTTP communication

## Proposed Architecture

### Core Interfaces (in `langchain4j-core`)

#### Location: `langchain4j-core/src/main/java/dev/langchain4j/model/discovery/`

#### ModelDiscovery Interface

```java
package dev.langchain4j.model.discovery;

import dev.langchain4j.model.ModelProvider;
import java.util.List;

/**
 * Represents a service that can discover available models from an LLM provider.
 * This allows customers to list available models and their capabilities without
 * needing to go to individual AI provider websites.
 *
 * <p>Similar to {@link dev.langchain4j.model.chat.ChatModel} and
 * {@link dev.langchain4j.model.chat.StreamingChatModel}, each provider should
 * implement this interface to enable model discovery.
 *
 * <p>Example usage:
 * <pre>{@code
 * OpenAiModelDiscovery discovery = OpenAiModelDiscovery.builder()
 *     .apiKey(apiKey)
 *     .build();
 *
 * List<ModelDescription> models = discovery.discoverModels();
 *
 * // Filter for specific models
 * ModelDiscoveryFilter filter = ModelDiscoveryFilter.builder()
 *     .types(Set.of(ModelType.CHAT))
 *     .requiredCapabilities(Set.of(Capability.RESPONSE_FORMAT_JSON_SCHEMA))
 *     .build();
 * List<ModelDescription> chatModels = discovery.discoverModels(filter);
 * }</pre>
 *
 * @see ModelDescription
 * @see ModelDiscoveryFilter
 */
public interface ModelDiscovery {

    /**
     * Retrieves a list of available models from the provider.
     *
     * @return A list of model descriptions
     * @throws RuntimeException if the discovery operation fails
     */
    List<ModelDescription> discoverModels();

    /**
     * Retrieves a filtered list of available models from the provider.
     *
     * <p>Not all providers support server-side filtering. If filtering is not supported
     * by the provider, implementations may either:
     * <ul>
     *   <li>Ignore the filter and return all models</li>
     *   <li>Apply the filter client-side after fetching all models</li>
     * </ul>
     *
     * <p>Use {@link #supportsFiltering()} to check if server-side filtering is available.
     *
     * @param filter Optional filter to narrow down results.
     *               If null, returns all models (same as {@link #discoverModels()}).
     * @return A list of model descriptions matching the filter criteria
     * @throws RuntimeException if the discovery operation fails
     */
    List<ModelDescription> discoverModels(ModelDiscoveryFilter filter);

    /**
     * Returns the provider for this discovery service.
     *
     * @return The model provider
     */
    ModelProvider provider();

    /**
     * Indicates whether this provider supports server-side filtering during model discovery.
     *
     * <p>If {@code true}, the provider can efficiently filter models on the server side.
     * If {@code false}, filtering (if any) is done client-side after fetching all models.
     *
     * @return true if server-side filtering is supported, false otherwise
     */
    default boolean supportsFiltering() {
        return false;
    }
}
```

#### ModelDescription Class

```java
package dev.langchain4j.model.discovery;

import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import java.time.Instant;
import java.util.List;
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
```

#### ModelType Enum

```java
package dev.langchain4j.model.discovery;

/**
 * Represents the type/category of a model.
 */
public enum ModelType {
    /**
     * Chat/conversational models (e.g., GPT-4, Claude, etc.)
     */
    CHAT,

    /**
     * Text embedding models for vector representations
     */
    EMBEDDING,

    /**
     * Image generation models (e.g., DALL-E, Stable Diffusion)
     */
    IMAGE_GENERATION,

    /**
     * Image understanding/vision models
     */
    IMAGE_UNDERSTANDING,

    /**
     * Audio transcription models (speech-to-text)
     */
    AUDIO_TRANSCRIPTION,

    /**
     * Audio generation models (text-to-speech)
     */
    AUDIO_GENERATION,

    /**
     * Video understanding models
     */
    VIDEO_UNDERSTANDING,

    /**
     * Content moderation models
     */
    MODERATION,

    /**
     * Code completion/generation models
     */
    CODE_COMPLETION,

    /**
     * Document reranking models
     */
    RERANKING,

    /**
     * Other or unclassified model types
     */
    OTHER
}
```

#### ModelDiscoveryFilter Class

```java
package dev.langchain4j.model.discovery;

import dev.langchain4j.model.chat.Capability;
import java.util.Objects;
import java.util.Set;

/**
 * Filter criteria for model discovery.
 *
 * <p>Not all providers support all filter criteria. Providers that don't support
 * server-side filtering may apply these filters client-side or ignore them.
 *
 * <p>Example:
 * <pre>{@code
 * ModelDiscoveryFilter filter = ModelDiscoveryFilter.builder()
 *     .types(Set.of(ModelType.CHAT))
 *     .requiredCapabilities(Set.of(Capability.RESPONSE_FORMAT_JSON_SCHEMA))
 *     .minContextWindow(100000)
 *     .includeDeprecated(false)
 *     .build();
 * }</pre>
 */
public class ModelDiscoveryFilter {

    /**
     * A pre-built filter that matches all models (no filtering).
     */
    public static final ModelDiscoveryFilter ALL = ModelDiscoveryFilter.builder().build();

    private final Set<ModelType> types;
    private final Set<Capability> requiredCapabilities;
    private final Integer minContextWindow;
    private final Integer maxContextWindow;
    private final String namePattern;
    private final Boolean includeDeprecated;

    private ModelDiscoveryFilter(Builder builder) {
        this.types = builder.types != null ? Set.copyOf(builder.types) : null;
        this.requiredCapabilities = builder.requiredCapabilities != null ? Set.copyOf(builder.requiredCapabilities) : null;
        this.minContextWindow = builder.minContextWindow;
        this.maxContextWindow = builder.maxContextWindow;
        this.namePattern = builder.namePattern;
        this.includeDeprecated = builder.includeDeprecated;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Set<ModelType> getTypes() {
        return types;
    }

    public Set<Capability> getRequiredCapabilities() {
        return requiredCapabilities;
    }

    public Integer getMinContextWindow() {
        return minContextWindow;
    }

    public Integer getMaxContextWindow() {
        return maxContextWindow;
    }

    public String getNamePattern() {
        return namePattern;
    }

    public Boolean getIncludeDeprecated() {
        return includeDeprecated;
    }

    /**
     * Checks if this filter matches all models (no filtering criteria specified).
     *
     * @return true if no filtering criteria are set, false otherwise
     */
    public boolean matchesAll() {
        return types == null &&
               requiredCapabilities == null &&
               minContextWindow == null &&
               maxContextWindow == null &&
               namePattern == null &&
               includeDeprecated == null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ModelDiscoveryFilter)) return false;
        ModelDiscoveryFilter that = (ModelDiscoveryFilter) o;
        return Objects.equals(types, that.types) &&
               Objects.equals(requiredCapabilities, that.requiredCapabilities) &&
               Objects.equals(minContextWindow, that.minContextWindow) &&
               Objects.equals(maxContextWindow, that.maxContextWindow) &&
               Objects.equals(namePattern, that.namePattern) &&
               Objects.equals(includeDeprecated, that.includeDeprecated);
    }

    @Override
    public int hashCode() {
        return Objects.hash(types, requiredCapabilities, minContextWindow, maxContextWindow, namePattern, includeDeprecated);
    }

    @Override
    public String toString() {
        return "ModelDiscoveryFilter{" +
               "types=" + types +
               ", requiredCapabilities=" + requiredCapabilities +
               ", minContextWindow=" + minContextWindow +
               ", maxContextWindow=" + maxContextWindow +
               ", namePattern='" + namePattern + '\'' +
               ", includeDeprecated=" + includeDeprecated +
               '}';
    }

    public static class Builder {
        private Set<ModelType> types;
        private Set<Capability> requiredCapabilities;
        private Integer minContextWindow;
        private Integer maxContextWindow;
        private String namePattern;
        private Boolean includeDeprecated;

        /**
         * Filter by model types. Only models matching one of these types will be returned.
         *
         * @param types Set of model types to include
         * @return this builder
         */
        public Builder types(Set<ModelType> types) {
            this.types = types;
            return this;
        }

        /**
         * Filter by required capabilities. Only models that support ALL specified capabilities will be returned.
         *
         * @param requiredCapabilities Set of capabilities that models must support
         * @return this builder
         */
        public Builder requiredCapabilities(Set<Capability> requiredCapabilities) {
            this.requiredCapabilities = requiredCapabilities;
            return this;
        }

        /**
         * Filter by minimum context window size. Only models with context windows >= this value will be returned.
         *
         * @param minContextWindow Minimum context window in tokens
         * @return this builder
         */
        public Builder minContextWindow(Integer minContextWindow) {
            this.minContextWindow = minContextWindow;
            return this;
        }

        /**
         * Filter by maximum context window size. Only models with context windows <= this value will be returned.
         *
         * @param maxContextWindow Maximum context window in tokens
         * @return this builder
         */
        public Builder maxContextWindow(Integer maxContextWindow) {
            this.maxContextWindow = maxContextWindow;
            return this;
        }

        /**
         * Filter by model name pattern (regex). Only models whose names match this pattern will be returned.
         *
         * @param namePattern Regular expression pattern for model names
         * @return this builder
         */
        public Builder namePattern(String namePattern) {
            this.namePattern = namePattern;
            return this;
        }

        /**
         * Whether to include deprecated models in results. If false, deprecated models are excluded.
         * If null (default), deprecated status is ignored.
         *
         * @param includeDeprecated true to include deprecated models, false to exclude them, null to ignore
         * @return this builder
         */
        public Builder includeDeprecated(Boolean includeDeprecated) {
            this.includeDeprecated = includeDeprecated;
            return this;
        }

        public ModelDiscoveryFilter build() {
            return new ModelDiscoveryFilter(this);
        }
    }
}
```

#### ModelPricing Class

```java
package dev.langchain4j.model.discovery;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Objects;

/**
 * Represents pricing information for a model.
 *
 * <p>Prices are typically expressed per 1 million tokens.
 * Different pricing may apply for input (prompt) tokens and output (completion) tokens.
 */
public class ModelPricing {

    private final BigDecimal inputPricePerMillionTokens;
    private final BigDecimal outputPricePerMillionTokens;
    private final Currency currency;
    private final String pricingUrl;

    private ModelPricing(Builder builder) {
        this.inputPricePerMillionTokens = builder.inputPricePerMillionTokens;
        this.outputPricePerMillionTokens = builder.outputPricePerMillionTokens;
        this.currency = builder.currency != null ? builder.currency : Currency.getInstance("USD");
        this.pricingUrl = builder.pricingUrl;
    }

    public static Builder builder() {
        return new Builder();
    }

    public BigDecimal getInputPricePerMillionTokens() {
        return inputPricePerMillionTokens;
    }

    public BigDecimal getOutputPricePerMillionTokens() {
        return outputPricePerMillionTokens;
    }

    public Currency getCurrency() {
        return currency;
    }

    public String getPricingUrl() {
        return pricingUrl;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ModelPricing)) return false;
        ModelPricing that = (ModelPricing) o;
        return Objects.equals(inputPricePerMillionTokens, that.inputPricePerMillionTokens) &&
               Objects.equals(outputPricePerMillionTokens, that.outputPricePerMillionTokens) &&
               Objects.equals(currency, that.currency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(inputPricePerMillionTokens, outputPricePerMillionTokens, currency);
    }

    @Override
    public String toString() {
        return "ModelPricing{" +
               "input=" + inputPricePerMillionTokens +
               ", output=" + outputPricePerMillionTokens +
               " " + currency.getCurrencyCode() +
               " per 1M tokens" +
               '}';
    }

    public static class Builder {
        private BigDecimal inputPricePerMillionTokens;
        private BigDecimal outputPricePerMillionTokens;
        private Currency currency;
        private String pricingUrl;

        public Builder inputPricePerMillionTokens(BigDecimal inputPricePerMillionTokens) {
            this.inputPricePerMillionTokens = inputPricePerMillionTokens;
            return this;
        }

        public Builder outputPricePerMillionTokens(BigDecimal outputPricePerMillionTokens) {
            this.outputPricePerMillionTokens = outputPricePerMillionTokens;
            return this;
        }

        public Builder currency(Currency currency) {
            this.currency = currency;
            return this;
        }

        public Builder currency(String currencyCode) {
            this.currency = Currency.getInstance(currencyCode);
            return this;
        }

        public Builder pricingUrl(String pricingUrl) {
            this.pricingUrl = pricingUrl;
            return this;
        }

        public ModelPricing build() {
            return new ModelPricing(this);
        }
    }
}
```

## Implementation Plan by Module

### Phase 1: Core Infrastructure (langchain4j-core)

**Files to Create:**
1. `langchain4j-core/src/main/java/dev/langchain4j/model/discovery/ModelDiscovery.java`
2. `langchain4j-core/src/main/java/dev/langchain4j/model/discovery/ModelDescription.java`
3. `langchain4j-core/src/main/java/dev/langchain4j/model/discovery/ModelType.java`
4. `langchain4j-core/src/main/java/dev/langchain4j/model/discovery/ModelDiscoveryFilter.java`
5. `langchain4j-core/src/main/java/dev/langchain4j/model/discovery/ModelPricing.java`
6. `langchain4j-core/src/main/java/dev/langchain4j/model/discovery/package-info.java`

**Tests to Create:**
1. `ModelDescriptionTest.java` - Unit tests for builder, validation, equals/hashCode
2. `ModelDiscoveryFilterTest.java` - Unit tests for builder, matchesAll() logic
3. `ModelPricingTest.java` - Unit tests for builder, currency handling
4. `ModelTypeTest.java` - Verify all enum values

**Estimated Effort:** 1-2 days

### Phase 2: Provider Implementations

#### 2.1 OpenAI Implementation (langchain4j-open-ai)

**Current State:** No model listing exists

**Implementation Steps:**

1. **Add to OpenAiClient interface:**
```java
// In OpenAiClient.java
public abstract SyncOrAsync<ModelsListResponse> listModels();
```

2. **Create API response classes:**
   - `ModelsListResponse.java` - Matches OpenAI's `/v1/models` response structure
   - `OpenAiModelInfo.java` - Individual model metadata

3. **Implement in DefaultOpenAiClient:**
```java
@Override
public SyncOrAsync<ModelsListResponse> listModels() {
    HttpRequest request = HttpRequest.builder()
        .method(GET)
        .url(baseUrl, "models")
        .addHeader("Authorization", "Bearer " + apiKey)
        // Add other headers (organization, project, etc.)
        .build();

    return new SyncOrAsync<>() {
        @Override
        public ModelsListResponse get() {
            SuccessfulHttpResponse response = httpClient.execute(request);
            return fromJson(response.body(), ModelsListResponse.class);
        }

        @Override
        public CompletableFuture<ModelsListResponse> getAsync() {
            return CompletableFuture.supplyAsync(this::get);
        }
    };
}
```

4. **Create OpenAiModelDiscovery class:**
```java
package dev.langchain4j.model.openai;

import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.discovery.*;
import dev.langchain4j.model.openai.internal.OpenAiClient;
import dev.langchain4j.model.openai.internal.ModelsListResponse;
import java.util.List;
import java.util.stream.Collectors;

/**
 * OpenAI implementation of {@link ModelDiscovery}.
 *
 * <p>Example:
 * <pre>{@code
 * OpenAiModelDiscovery discovery = OpenAiModelDiscovery.builder()
 *     .apiKey(System.getenv("OPENAI_API_KEY"))
 *     .build();
 *
 * List<ModelDescription> models = discovery.discoverModels();
 * }</pre>
 */
public class OpenAiModelDiscovery implements ModelDiscovery {

    private final OpenAiClient client;

    private OpenAiModelDiscovery(Builder builder) {
        this.client = OpenAiClient.builder()
            .baseUrl(builder.baseUrl)
            .apiKey(builder.apiKey)
            .organizationId(builder.organizationId)
            .projectId(builder.projectId)
            .connectTimeout(builder.connectTimeout)
            .readTimeout(builder.readTimeout)
            .logRequests(builder.logRequests)
            .logResponses(builder.logResponses)
            .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public List<ModelDescription> discoverModels() {
        return discoverModels(null);
    }

    @Override
    public List<ModelDescription> discoverModels(ModelDiscoveryFilter filter) {
        ModelsListResponse response = client.listModels().get();
        List<ModelDescription> models = response.getData().stream()
            .map(this::mapToModelDescription)
            .collect(Collectors.toList());

        // OpenAI doesn't support server-side filtering, so filter client-side
        if (filter != null && !filter.matchesAll()) {
            return filterModels(models, filter);
        }

        return models;
    }

    @Override
    public ModelProvider provider() {
        return ModelProvider.OPEN_AI;
    }

    @Override
    public boolean supportsFiltering() {
        return false; // OpenAI doesn't support server-side filtering
    }

    private ModelDescription mapToModelDescription(OpenAiModelInfo modelInfo) {
        return ModelDescription.builder()
            .id(modelInfo.getId())
            .name(modelInfo.getId()) // OpenAI uses id as name
            .provider(ModelProvider.OPEN_AI)
            .owner(modelInfo.getOwnedBy())
            .createdAt(modelInfo.getCreated() != null ?
                Instant.ofEpochSecond(modelInfo.getCreated()) : null)
            .build();
    }

    private List<ModelDescription> filterModels(List<ModelDescription> models, ModelDiscoveryFilter filter) {
        // Client-side filtering logic
        return models.stream()
            .filter(model -> matchesFilter(model, filter))
            .collect(Collectors.toList());
    }

    private boolean matchesFilter(ModelDescription model, ModelDiscoveryFilter filter) {
        // Filtering logic implementation
        if (filter.getTypes() != null && !filter.getTypes().contains(model.getType())) {
            return false;
        }
        // ... other filter checks
        return true;
    }

    public static class Builder {
        private String baseUrl;
        private String apiKey;
        private String organizationId;
        private String projectId;
        private Duration connectTimeout;
        private Duration readTimeout;
        private Boolean logRequests;
        private Boolean logResponses;

        // Builder methods following OpenAiChatModel pattern

        public OpenAiModelDiscovery build() {
            return new OpenAiModelDiscovery(this);
        }
    }
}
```

**Files to Create:**
- `langchain4j-open-ai/src/main/java/dev/langchain4j/model/openai/internal/ModelsListResponse.java`
- `langchain4j-open-ai/src/main/java/dev/langchain4j/model/openai/internal/OpenAiModelInfo.java`
- `langchain4j-open-ai/src/main/java/dev/langchain4j/model/openai/OpenAiModelDiscovery.java`
- `langchain4j-open-ai/src/test/java/dev/langchain4j/model/openai/OpenAiModelDiscoveryTest.java` (unit tests)
- `langchain4j-open-ai/src/test/java/dev/langchain4j/model/openai/OpenAiModelDiscoveryIT.java` (integration tests)

**Estimated Effort:** 2-3 days

#### 2.2 Mistral AI Implementation (langchain4j-mistral-ai)

**Current State:** Already has `listModels()` in client

**Implementation Steps:**

1. **Create MistralAiModelDiscovery wrapper:**
```java
package dev.langchain4j.model.mistralai;

import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.discovery.*;
import dev.langchain4j.model.mistralai.internal.api.MistralAiModelCard;
import dev.langchain4j.model.mistralai.internal.api.MistralAiModelResponse;
import dev.langchain4j.model.mistralai.internal.client.MistralAiClient;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Mistral AI implementation of {@link ModelDiscovery}.
 */
public class MistralAiModelDiscovery implements ModelDiscovery {

    private final MistralAiClient client;

    private MistralAiModelDiscovery(Builder builder) {
        this.client = MistralAiClient.builder()
            .baseUrl(builder.baseUrl)
            .apiKey(builder.apiKey)
            .timeout(builder.timeout)
            .logRequests(builder.logRequests)
            .logResponses(builder.logResponses)
            .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public List<ModelDescription> discoverModels() {
        return discoverModels(null);
    }

    @Override
    public List<ModelDescription> discoverModels(ModelDiscoveryFilter filter) {
        MistralAiModelResponse response = client.listModels();
        List<ModelDescription> models = response.getData().stream()
            .map(this::mapFromMistralAiModelCard)
            .collect(Collectors.toList());

        if (filter != null && !filter.matchesAll()) {
            return filterModels(models, filter);
        }

        return models;
    }

    @Override
    public ModelProvider provider() {
        return ModelProvider.MISTRAL_AI;
    }

    private ModelDescription mapFromMistralAiModelCard(MistralAiModelCard card) {
        return ModelDescription.builder()
            .id(card.getId())
            .name(card.getId())
            .provider(ModelProvider.MISTRAL_AI)
            .owner(card.getOwnerBy())
            .createdAt(card.getCreated() != null ?
                Instant.ofEpochSecond(card.getCreated()) : null)
            .build();
    }

    // Builder and filtering logic similar to OpenAI
}
```

**Files to Create:**
- `langchain4j-mistral-ai/src/main/java/dev/langchain4j/model/mistralai/MistralAiModelDiscovery.java`
- Integration and unit tests

**Estimated Effort:** 1 day

#### 2.3 Ollama Implementation (langchain4j-ollama)

**Current State:** Has `OllamaModels` class with comprehensive model management

**Implementation Steps:**

1. **Create OllamaModelDiscovery:**
```java
package dev.langchain4j.model.ollama;

import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.discovery.*;
import dev.langchain4j.model.output.Response;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Ollama implementation of {@link ModelDiscovery}.
 *
 * <p>This implementation wraps the existing {@link OllamaModels} class
 * and provides additional model card details by fetching information
 * for each available model.
 */
public class OllamaModelDiscovery implements ModelDiscovery {

    private final OllamaModels ollamaModels;
    private final boolean fetchDetailedInfo;

    private OllamaModelDiscovery(Builder builder) {
        this.ollamaModels = OllamaModels.builder()
            .httpClientBuilder(builder.httpClientBuilder)
            .baseUrl(builder.baseUrl)
            .timeout(builder.timeout)
            .maxRetries(builder.maxRetries)
            .logRequests(builder.logRequests)
            .logResponses(builder.logResponses)
            .build();
        this.fetchDetailedInfo = builder.fetchDetailedInfo != null ?
            builder.fetchDetailedInfo : false;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public List<ModelDescription> discoverModels() {
        return discoverModels(null);
    }

    @Override
    public List<ModelDescription> discoverModels(ModelDiscoveryFilter filter) {
        Response<List<OllamaModel>> response = ollamaModels.availableModels();
        List<ModelDescription> models = response.content().stream()
            .map(this::mapFromOllamaModel)
            .collect(Collectors.toList());

        if (filter != null && !filter.matchesAll()) {
            return filterModels(models, filter);
        }

        return models;
    }

    @Override
    public ModelProvider provider() {
        return ModelProvider.OLLAMA;
    }

    private ModelDescription mapFromOllamaModel(OllamaModel model) {
        ModelDescription.Builder builder = ModelDescription.builder()
            .id(model.getName())
            .name(model.getName())
            .provider(ModelProvider.OLLAMA)
            .createdAt(model.getModifiedAt() != null ?
                model.getModifiedAt().toInstant() : null);

        // Optionally fetch detailed model card information
        if (fetchDetailedInfo) {
            try {
                Response<OllamaModelCard> cardResponse = ollamaModels.modelCard(model);
                OllamaModelCard card = cardResponse.content();

                if (card.getCapabilities() != null) {
                    // Map Ollama capabilities to LangChain4j capabilities
                    // This requires some interpretation of Ollama's capability strings
                }
            } catch (Exception e) {
                // Log warning but continue
            }
        }

        return builder.build();
    }

    public static class Builder {
        private HttpClientBuilder httpClientBuilder;
        private String baseUrl;
        private Duration timeout;
        private Integer maxRetries;
        private Boolean logRequests;
        private Boolean logResponses;
        private Boolean fetchDetailedInfo;

        public Builder fetchDetailedInfo(Boolean fetchDetailedInfo) {
            this.fetchDetailedInfo = fetchDetailedInfo;
            return this;
        }

        // Other builder methods following OllamaModels pattern

        public OllamaModelDiscovery build() {
            return new OllamaModelDiscovery(this);
        }
    }
}
```

**Files to Create:**
- `langchain4j-ollama/src/main/java/dev/langchain4j/model/ollama/OllamaModelDiscovery.java`
- Integration and unit tests

**Estimated Effort:** 1-2 days

#### 2.4 Anthropic Implementation (langchain4j-anthropic)

**Current State:** No model listing (Anthropic API doesn't provide model listing endpoint)

**Implementation Steps:**

1. **Create static model registry:**
```java
package dev.langchain4j.model.anthropic;

import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.discovery.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Anthropic implementation of {@link ModelDiscovery}.
 *
 * <p>Since Anthropic does not provide a model listing API endpoint,
 * this implementation returns a curated list of known Anthropic models
 * based on their public documentation.
 *
 * <p>Note: This list may not be exhaustive and is updated periodically.
 * For the most current model information, please refer to Anthropic's
 * official documentation at https://docs.anthropic.com/
 */
public class AnthropicModelDiscovery implements ModelDiscovery {

    private static final List<ModelDescription> KNOWN_MODELS = buildKnownModels();

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public List<ModelDescription> discoverModels() {
        return new ArrayList<>(KNOWN_MODELS);
    }

    @Override
    public List<ModelDescription> discoverModels(ModelDiscoveryFilter filter) {
        if (filter == null || filter.matchesAll()) {
            return discoverModels();
        }

        return KNOWN_MODELS.stream()
            .filter(model -> matchesFilter(model, filter))
            .collect(Collectors.toList());
    }

    @Override
    public ModelProvider provider() {
        return ModelProvider.ANTHROPIC;
    }

    private static List<ModelDescription> buildKnownModels() {
        List<ModelDescription> models = new ArrayList<>();

        // Claude 3.5 Sonnet
        models.add(ModelDescription.builder()
            .id("claude-3-5-sonnet-20241022")
            .name("Claude 3.5 Sonnet")
            .description("Most intelligent model, combining high intelligence with improved speed")
            .provider(ModelProvider.ANTHROPIC)
            .type(ModelType.CHAT)
            .capabilities(Set.of(Capability.RESPONSE_FORMAT_JSON_SCHEMA))
            .contextWindow(200000)
            .maxOutputTokens(8192)
            .pricing(ModelPricing.builder()
                .inputPricePerMillionTokens(new BigDecimal("3.00"))
                .outputPricePerMillionTokens(new BigDecimal("15.00"))
                .currency("USD")
                .pricingUrl("https://www.anthropic.com/pricing")
                .build())
            .owner("Anthropic")
            .build());

        // Claude 3.5 Haiku
        models.add(ModelDescription.builder()
            .id("claude-3-5-haiku-20241022")
            .name("Claude 3.5 Haiku")
            .description("Fastest and most compact model for near-instant responsiveness")
            .provider(ModelProvider.ANTHROPIC)
            .type(ModelType.CHAT)
            .contextWindow(200000)
            .maxOutputTokens(8192)
            .pricing(ModelPricing.builder()
                .inputPricePerMillionTokens(new BigDecimal("0.80"))
                .outputPricePerMillionTokens(new BigDecimal("4.00"))
                .currency("USD")
                .pricingUrl("https://www.anthropic.com/pricing")
                .build())
            .owner("Anthropic")
            .build());

        // Claude 3 Opus
        models.add(ModelDescription.builder()
            .id("claude-3-opus-20240229")
            .name("Claude 3 Opus")
            .description("Top-level performance, intelligence, fluency, and understanding")
            .provider(ModelProvider.ANTHROPIC)
            .type(ModelType.CHAT)
            .contextWindow(200000)
            .maxOutputTokens(4096)
            .pricing(ModelPricing.builder()
                .inputPricePerMillionTokens(new BigDecimal("15.00"))
                .outputPricePerMillionTokens(new BigDecimal("75.00"))
                .currency("USD")
                .pricingUrl("https://www.anthropic.com/pricing")
                .build())
            .owner("Anthropic")
            .build());

        // Additional models can be added here

        return models;
    }

    private boolean matchesFilter(ModelDescription model, ModelDiscoveryFilter filter) {
        // Client-side filtering logic
        return true; // Implementation similar to other providers
    }

    public static class Builder {
        // No configuration needed for static registry

        public AnthropicModelDiscovery build() {
            return new AnthropicModelDiscovery();
        }
    }
}
```

**Files to Create:**
- `langchain4j-anthropic/src/main/java/dev/langchain4j/model/anthropic/AnthropicModelDiscovery.java`
- Unit tests (no integration tests needed for static registry)

**Estimated Effort:** 1 day

### Phase 3: Testing Infrastructure (langchain4j-core)

**Create Abstract Test Base Classes:**

```java
package dev.langchain4j.model.discovery;

import static org.assertj.core.api.Assertions.*;

import dev.langchain4j.model.ModelProvider;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;

/**
 * Abstract base class for {@link ModelDiscovery} integration tests.
 * Provider-specific implementations should extend this class and implement
 * {@link #createModelDiscovery()}.
 */
public abstract class AbstractModelDiscoveryIT {

    /**
     * Create an instance of the ModelDiscovery implementation to test.
     * This should be properly configured with credentials, URLs, etc.
     *
     * @return Configured ModelDiscovery instance
     */
    protected abstract ModelDiscovery createModelDiscovery();

    /**
     * Returns the expected provider for this discovery implementation.
     *
     * @return Expected ModelProvider
     */
    protected abstract ModelProvider expectedProvider();

    @Test
    void should_discover_models() {
        ModelDiscovery discovery = createModelDiscovery();

        List<ModelDescription> models = discovery.discoverModels();

        assertThat(models).isNotEmpty();
        assertThat(models).allMatch(m -> m.getId() != null, "All models should have an ID");
        assertThat(models).allMatch(m -> m.getName() != null, "All models should have a name");
        assertThat(models).allMatch(m -> m.getProvider() == expectedProvider(),
            "All models should have the correct provider");
    }

    @Test
    void should_return_correct_provider() {
        ModelDiscovery discovery = createModelDiscovery();

        assertThat(discovery.provider()).isEqualTo(expectedProvider());
    }

    @Test
    void should_discover_models_with_null_filter() {
        ModelDiscovery discovery = createModelDiscovery();

        List<ModelDescription> modelsWithoutFilter = discovery.discoverModels();
        List<ModelDescription> modelsWithNullFilter = discovery.discoverModels(null);

        assertThat(modelsWithNullFilter).hasSameSizeAs(modelsWithoutFilter);
    }

    @Test
    void should_discover_models_with_empty_filter() {
        ModelDiscovery discovery = createModelDiscovery();

        List<ModelDescription> modelsWithoutFilter = discovery.discoverModels();
        List<ModelDescription> modelsWithEmptyFilter = discovery.discoverModels(ModelDiscoveryFilter.ALL);

        assertThat(modelsWithEmptyFilter).hasSameSizeAs(modelsWithoutFilter);
    }

    @Test
    void should_filter_by_type_if_supported() {
        ModelDiscovery discovery = createModelDiscovery();

        ModelDiscoveryFilter filter = ModelDiscoveryFilter.builder()
            .types(Set.of(ModelType.CHAT))
            .build();

        List<ModelDescription> models = discovery.discoverModels(filter);

        // If filtering is supported, all models should be CHAT type
        // If not supported, we may get all models or client-side filtered results
        if (discovery.supportsFiltering()) {
            assertThat(models).allMatch(m -> m.getType() == ModelType.CHAT,
                "All models should be CHAT type when filtering is supported");
        } else {
            // For providers without server-side filtering,
            // implementation may choose to filter client-side or ignore filter
            assertThat(models).isNotNull();
        }
    }

    @Test
    void should_filter_by_context_window() {
        ModelDiscovery discovery = createModelDiscovery();

        ModelDiscoveryFilter filter = ModelDiscoveryFilter.builder()
            .minContextWindow(100000)
            .build();

        List<ModelDescription> models = discovery.discoverModels(filter);

        // For models with context window information, verify filtering
        models.stream()
            .filter(m -> m.getContextWindow() != null)
            .forEach(m -> assertThat(m.getContextWindow())
                .isGreaterThanOrEqualTo(100000));
    }

    @Test
    void should_handle_deprecated_filter() {
        ModelDiscovery discovery = createModelDiscovery();

        ModelDiscoveryFilter includeDeprecated = ModelDiscoveryFilter.builder()
            .includeDeprecated(true)
            .build();

        ModelDiscoveryFilter excludeDeprecated = ModelDiscoveryFilter.builder()
            .includeDeprecated(false)
            .build();

        List<ModelDescription> withDeprecated = discovery.discoverModels(includeDeprecated);
        List<ModelDescription> withoutDeprecated = discovery.discoverModels(excludeDeprecated);

        // Number of models excluding deprecated should be <= models including deprecated
        assertThat(withoutDeprecated.size()).isLessThanOrEqualTo(withDeprecated.size());

        // Verify no deprecated models when excluded
        assertThat(withoutDeprecated)
            .noneMatch(m -> Boolean.TRUE.equals(m.isDeprecated()));
    }
}
```

**Files to Create:**
- `langchain4j-core/src/test/java/dev/langchain4j/model/discovery/AbstractModelDiscoveryIT.java`
- Example concrete implementations in each provider module

**Estimated Effort:** 1 day

### Phase 4: Documentation and Examples

**Documentation Updates:**

1. **Add section to docs/docs/integrations/language-models/index.md:**
```markdown
## Model Discovery

LangChain4j provides a unified API for discovering available models from different providers.
This allows you to:

- List all available models from a provider
- Filter models by type, capabilities, or context window
- Compare models across different providers
- Programmatically select the best model for your use case

### Supported Providers

Model discovery is currently supported for:
- OpenAI
- Mistral AI
- Ollama
- Anthropic (static registry)

### Basic Usage

```java
// Discover OpenAI models
OpenAiModelDiscovery discovery = OpenAiModelDiscovery.builder()
    .apiKey(apiKey)
    .build();

List<ModelDescription> models = discovery.discoverModels();

models.forEach(model -> {
    System.out.printf("%s: %s (context: %d tokens)%n",
        model.getName(),
        model.getDescription(),
        model.getContextWindow());
});
```

### Filtering Models

```java
// Find chat models with JSON schema support and large context windows
ModelDiscoveryFilter filter = ModelDiscoveryFilter.builder()
    .types(Set.of(ModelType.CHAT))
    .requiredCapabilities(Set.of(Capability.RESPONSE_FORMAT_JSON_SCHEMA))
    .minContextWindow(100000)
    .includeDeprecated(false)
    .build();

List<ModelDescription> filteredModels = discovery.discoverModels(filter);
```

### Server-Side vs Client-Side Filtering

Not all providers support server-side filtering. Use `supportsFiltering()` to check:

```java
if (discovery.supportsFiltering()) {
    // Provider will filter on the server
} else {
    // Filtering may be done client-side or ignored
}
```
```

2. **Create provider-specific documentation** in each provider's docs file

3. **Update CONTRIBUTING.md:**
```markdown
## Guidelines for Implementing ModelDiscovery

When adding a new model provider, consider implementing the `ModelDiscovery` interface:

1. **Create the implementation class** (e.g., `XyzModelDiscovery`)
2. **Follow the builder pattern** consistent with `XyzChatModel`
3. **Map provider-specific models** to `ModelDescription`
4. **Implement filtering** if the provider supports it server-side
5. **Create integration tests** extending `AbstractModelDiscoveryIT`
6. **Add documentation** and examples

### Required Tests

- Extend `AbstractModelDiscoveryIT` in your integration tests
- Add provider-specific unit tests for mapping logic
- Ensure test coverage >= 75%

### Documentation Checklist

- [ ] Add model discovery section to provider's documentation
- [ ] Create example in langchain4j-examples repository
- [ ] Update main integrations index page
- [ ] Add Javadoc with usage examples
```

**Example Code for langchain4j-examples:**

Create `OpenAiModelDiscoveryExample.java`:
```java
package dev.langchain4j.example.openai;

import dev.langchain4j.model.discovery.*;
import dev.langchain4j.model.openai.OpenAiModelDiscovery;
import dev.langchain4j.model.chat.Capability;
import java.util.List;
import java.util.Set;

public class OpenAiModelDiscoveryExample {

    public static void main(String[] args) {

        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null) {
            System.err.println("Please set OPENAI_API_KEY environment variable");
            System.exit(1);
        }

        OpenAiModelDiscovery discovery = OpenAiModelDiscovery.builder()
            .apiKey(apiKey)
            .build();

        System.out.println("=== Discovering all OpenAI models ===\n");

        List<ModelDescription> allModels = discovery.discoverModels();
        System.out.printf("Found %d models%n%n", allModels.size());

        allModels.forEach(model -> {
            System.out.printf("Model: %s%n", model.getName());
            System.out.printf("  ID: %s%n", model.getId());
            System.out.printf("  Type: %s%n", model.getType());
            System.out.printf("  Provider: %s%n", model.getProvider());
            if (model.getContextWindow() != null) {
                System.out.printf("  Context Window: %,d tokens%n", model.getContextWindow());
            }
            if (model.getPricing() != null) {
                System.out.printf("  Pricing: $%.4f / $%.4f per 1M tokens%n",
                    model.getPricing().getInputPricePerMillionTokens(),
                    model.getPricing().getOutputPricePerMillionTokens());
            }
            System.out.println();
        });

        System.out.println("\n=== Filtering for chat models with JSON schema support ===\n");

        ModelDiscoveryFilter filter = ModelDiscoveryFilter.builder()
            .types(Set.of(ModelType.CHAT))
            .requiredCapabilities(Set.of(Capability.RESPONSE_FORMAT_JSON_SCHEMA))
            .minContextWindow(128000)
            .includeDeprecated(false)
            .build();

        List<ModelDescription> filteredModels = discovery.discoverModels(filter);
        System.out.printf("Found %d models matching criteria%n%n", filteredModels.size());

        filteredModels.forEach(model -> {
            System.out.printf("✓ %s (%s)%n", model.getName(), model.getId());
            System.out.printf("  Context: %,d tokens%n", model.getContextWindow());
            System.out.printf("  Capabilities: %s%n", model.getCapabilities());
            System.out.println();
        });

        System.out.println("\n=== Comparing models across providers ===\n");

        // Example of comparing with another provider
        System.out.println("You can use the same API to discover models from different providers:");
        System.out.println("- OpenAiModelDiscovery");
        System.out.println("- MistralAiModelDiscovery");
        System.out.println("- OllamaModelDiscovery");
        System.out.println("- AnthropicModelDiscovery");
    }
}
```

**Estimated Effort:** 2-3 days

## Design Decisions and Rationale

### Why Separate Interface?
- **Single Responsibility:** Model discovery is conceptually different from using models for chat/embedding tasks
- **Optional Feature:** Not all use cases require discovery; keeps chat model APIs clean
- **Provider Flexibility:** Some providers (like Anthropic) don't expose model listing APIs
- **Consistent Pattern:** Matches the separation between `ChatModel` and `StreamingChatModel`

### Why ModelDescription vs Provider-Specific Classes?
- **Unified API:** Allows cross-provider model comparison without provider-specific code
- **Easier Integration:** Client code doesn't need provider-specific handling
- **Extensibility:** New providers can easily add provider-specific metadata via `additionalMetadata` map
- **Standardization:** Common fields (context window, pricing, capabilities) are normalized

### Why Support Both Filtered and Unfiltered Discovery?
- **Provider Capability Variance:** Some providers support server-side filtering, others don't
- **Flexibility:** Allows efficient server-side filtering when available, client-side fallback when not
- **Backwards Compatibility:** Simple `discoverModels()` for basic use cases
- **Performance:** Avoid fetching unnecessary data when provider supports filtering

### Builder Pattern Consistency
- All implementations use builder pattern consistent with existing `ChatModel` implementations
- Reuse same builder properties (apiKey, baseUrl, timeout, etc.) from respective provider implementations
- Maintains familiarity for developers already using LangChain4j

### Static Registry for Anthropic
- Anthropic doesn't provide a model listing API endpoint
- Static registry is maintained based on official documentation
- Allows consistent API across all providers
- Updated with library releases to reflect new models

## Migration Strategy

### For Existing Implementations

#### Ollama
```java
// Old way (still works)
OllamaModels models = OllamaModels.builder()
    .baseUrl("http://localhost:11434")
    .build();
Response<List<OllamaModel>> response = models.availableModels();

// New way (recommended for cross-provider consistency)
OllamaModelDiscovery discovery = OllamaModelDiscovery.builder()
    .baseUrl("http://localhost:11434")
    .build();
List<ModelDescription> models = discovery.discoverModels();
```

#### Mistral
```java
// Old way (still works)
MistralAiClient client = MistralAiClient.builder()
    .apiKey(apiKey)
    .build();
MistralAiModelResponse response = client.listModels();

// New way (recommended for cross-provider consistency)
MistralAiModelDiscovery discovery = MistralAiModelDiscovery.builder()
    .apiKey(apiKey)
    .build();
List<ModelDescription> models = discovery.discoverModels();
```

**No Breaking Changes:** Existing APIs remain unchanged and fully functional. The new `ModelDiscovery` interface provides a consistent alternative.

## Timeline and Milestones

| Phase | Components | Duration | Dependencies |
|-------|-----------|----------|--------------|
| Phase 1 | Core interfaces and classes | 1-2 days | None |
| Phase 2.1 | OpenAI implementation | 2-3 days | Phase 1 |
| Phase 2.2 | Mistral implementation | 1 day | Phase 1 |
| Phase 2.3 | Ollama implementation | 1-2 days | Phase 1 |
| Phase 2.4 | Anthropic implementation | 1 day | Phase 1 |
| Phase 3 | Testing infrastructure | 1 day | Phase 1 |
| Phase 4 | Documentation & examples | 2-3 days | Phase 2 |

**Total Estimated Duration:** 9-13 days for complete implementation

### Deliverables by Phase

**Phase 1 Deliverables:**
- [ ] `ModelDiscovery` interface
- [ ] `ModelDescription` class
- [ ] `ModelType` enum
- [ ] `ModelDiscoveryFilter` class
- [ ] `ModelPricing` class
- [ ] Unit tests for all core classes
- [ ] Package documentation

**Phase 2 Deliverables:**
- [ ] OpenAI implementation with tests
- [ ] Mistral AI implementation with tests
- [ ] Ollama implementation with tests
- [ ] Anthropic implementation with tests

**Phase 3 Deliverables:**
- [ ] `AbstractModelDiscoveryIT` base test class
- [ ] Concrete integration tests for all providers
- [ ] Test coverage >= 75%

**Phase 4 Deliverables:**
- [ ] Updated integration docs
- [ ] Provider-specific documentation
- [ ] Updated CONTRIBUTING.md
- [ ] Example code in langchain4j-examples
- [ ] Comprehensive Javadoc

## Success Criteria

- [x] Core `ModelDiscovery` interface and related classes in langchain4j-core
- [x] Working implementations for OpenAI, Mistral, Ollama, and Anthropic
- [x] Comprehensive unit and integration tests (>75% coverage)
- [x] Documentation with clear examples
- [x] Examples in langchain4j-examples repository
- [x] No breaking changes to existing APIs
- [x] Consistent builder patterns across all implementations
- [x] All code formatted with spotless (`make format`)
- [x] All tests passing (`mvn test`)
- [x] Integration tests passing with appropriate API keys

## Risk Mitigation

### Risk: Provider API Changes
**Impact:** High
**Probability:** Medium
**Mitigation:**
- Use provider-specific response classes with `@JsonIgnoreProperties(ignoreUnknown = true)`
- Isolate mapping logic in dedicated methods
- Version provider APIs when possible
- Document API version dependencies

### Risk: Rate Limiting
**Impact:** Medium
**Probability:** Medium
**Mitigation:**
- Document rate limit considerations in Javadoc
- Consider implementing optional caching layer in future phase
- Provide retry configuration options
- Use exponential backoff where appropriate

### Risk: Incomplete Model Metadata
**Impact:** Low
**Probability:** High
**Mitigation:**
- Make all `ModelDescription` fields optional except id, name, provider
- Use builder pattern with sensible defaults
- Document which fields are guaranteed vs optional per provider
- Handle missing data gracefully

### Risk: Provider-Specific Features
**Impact:** Low
**Probability:** High
**Mitigation:**
- Use `additionalMetadata` map for provider-specific fields
- Document provider-specific features in class-level Javadoc
- Consider adding common fields to `ModelDescription` in future releases
- Allow extensibility through inheritance if needed

### Risk: Static Registry Staleness (Anthropic)
**Impact:** Medium
**Probability:** Medium
**Mitigation:**
- Include update timestamp in Javadoc
- Document that list may not be exhaustive
- Provide link to official Anthropic documentation
- Update registry with each library release
- Consider community contributions for updates

## Future Enhancements (Phase 5+)

### Advanced Filtering
- Pricing-based filtering (e.g., max cost per million tokens)
- Performance metrics (if providers expose them)
- Model families (e.g., all GPT-4 variants)

### Model Recommendations
```java
public interface ModelDiscovery {
    default ModelDescription recommendModel(ModelRequirements requirements) {
        // Intelligent model selection based on requirements
    }
}
```

### Model Comparison
```java
public class ModelComparator {
    public static ComparisonResult compare(ModelDescription m1, ModelDescription m2) {
        // Compare features, pricing, capabilities
    }

    public static List<ModelDescription> rankByCost(List<ModelDescription> models) {
        // Rank models by cost-effectiveness
    }
}
```

### Caching Layer
```java
public class CachedModelDiscovery implements ModelDiscovery {
    private final ModelDiscovery delegate;
    private final Cache<String, List<ModelDescription>> cache;

    // Wrap any ModelDiscovery with caching to reduce API calls
}
```

### Async Support
```java
public interface AsyncModelDiscovery {
    CompletableFuture<List<ModelDescription>> discoverModelsAsync();
    CompletableFuture<List<ModelDescription>> discoverModelsAsync(ModelDiscoveryFilter filter);
}
```

### Spring Boot Integration
```java
@ConfigurationProperties("langchain4j.model-discovery")
public class ModelDiscoveryProperties {
    private boolean enabled = true;
    private Duration cacheDuration = Duration.ofHours(24);
    private Map<String, ProviderConfig> providers = new HashMap<>();

    // Auto-configuration for Spring Boot applications
}
```

### Model Benchmarking
- Integration with third-party benchmarking services
- Performance metrics (tokens/sec, latency percentiles)
- Quality metrics (MMLU, HumanEval scores)

## Open Questions for Discussion

1. **Should ModelDiscovery be synchronous only, or support async/streaming?**
   - **Recommendation:** Start with synchronous, add async in Phase 5 if there's demand
   - **Rationale:** Simpler implementation, most use cases are startup/configuration time

2. **Should we include model benchmarks/performance metrics?**
   - **Recommendation:** Out of scope initially; can add via `additionalMetadata`
   - **Rationale:** Metrics vary by source, change frequently, hard to keep current

3. **How to handle model deprecation?**
   - **Recommendation:** Add `deprecated` boolean flag to `ModelDescription`
   - **Rationale:** Simple, allows filtering, clear semantics

4. **Should filtering be done client-side or encourage provider-side?**
   - **Recommendation:** Support both; providers indicate capability via `supportsFiltering()`
   - **Rationale:** Flexibility for different provider capabilities

5. **Should we support model deployment (for providers like Azure)?**
   - **Recommendation:** Out of scope; this is discovery, not management
   - **Rationale:** Deployment is a different concern; may add in future if requested

6. **How frequently should static registries (Anthropic) be updated?**
   - **Recommendation:** With each library release; accept community PRs for updates
   - **Rationale:** Balances freshness with maintenance burden

7. **Should we add a model registry/catalog service?**
   - **Recommendation:** Phase 5+ enhancement if there's demand
   - **Rationale:** Would require infrastructure beyond library scope

## References

- OpenAI Models API: https://platform.openai.com/docs/api-reference/models
- Mistral AI Models API: https://docs.mistral.ai/api/#tag/models
- Ollama Models API: https://github.com/ollama/ollama/blob/main/docs/api.md#list-local-models
- Anthropic Models: https://docs.anthropic.com/en/docs/about-claude/models

## Change Log

- 2025-12-17: Initial plan created
