package dev.langchain4j.model.mistralai;

import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.discovery.ModelDescription;
import dev.langchain4j.model.discovery.ModelDiscovery;
import dev.langchain4j.model.discovery.ModelDiscoveryFilter;
import dev.langchain4j.model.mistralai.internal.api.MistralAiModelCard;
import dev.langchain4j.model.mistralai.internal.api.MistralAiModelResponse;
import dev.langchain4j.model.mistralai.internal.client.MistralAiClient;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;

/**
 * Mistral AI implementation of {@link ModelDiscovery}.
 *
 * <p>Example:
 * <pre>{@code
 * MistralAiModelDiscovery discovery = MistralAiModelDiscovery.builder()
 *     .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
 *     .build();
 *
 * List<ModelDescription> models = discovery.discoverModels();
 * }</pre>
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
            .logger(builder.logger)
            .httpClientBuilder(builder.httpClientBuilder)
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

        // Mistral AI doesn't support server-side filtering, so filter client-side
        if (filter != null && !filter.matchesAll()) {
            return filterModels(models, filter);
        }

        return models;
    }

    @Override
    public ModelProvider provider() {
        return ModelProvider.MISTRAL_AI;
    }

    @Override
    public boolean supportsFiltering() {
        return false; // Mistral AI doesn't support server-side filtering
    }

    private ModelDescription mapFromMistralAiModelCard(MistralAiModelCard card) {
        return ModelDescription.builder()
            .id(card.getId())
            .name(card.getId()) // Mistral AI uses id as name
            .provider(ModelProvider.MISTRAL_AI)
            .owner(card.getOwnerBy())
            .createdAt(card.getCreated() != null ?
                Instant.ofEpochSecond(card.getCreated()) : null)
            .build();
    }

    private List<ModelDescription> filterModels(List<ModelDescription> models, ModelDiscoveryFilter filter) {
        return models.stream()
            .filter(model -> matchesFilter(model, filter))
            .collect(Collectors.toList());
    }

    private boolean matchesFilter(ModelDescription model, ModelDiscoveryFilter filter) {
        // Filter by type
        if (filter.getTypes() != null && !filter.getTypes().isEmpty()) {
            if (model.getType() == null || !filter.getTypes().contains(model.getType())) {
                return false;
            }
        }

        // Filter by required capabilities
        if (filter.getRequiredCapabilities() != null && !filter.getRequiredCapabilities().isEmpty()) {
            if (model.getCapabilities() == null ||
                !model.getCapabilities().containsAll(filter.getRequiredCapabilities())) {
                return false;
            }
        }

        // Filter by minimum context window
        if (filter.getMinContextWindow() != null) {
            if (model.getContextWindow() == null ||
                model.getContextWindow() < filter.getMinContextWindow()) {
                return false;
            }
        }

        // Filter by maximum context window
        if (filter.getMaxContextWindow() != null) {
            if (model.getContextWindow() == null ||
                model.getContextWindow() > filter.getMaxContextWindow()) {
                return false;
            }
        }

        // Filter by name pattern
        if (filter.getNamePattern() != null) {
            Pattern pattern = Pattern.compile(filter.getNamePattern());
            if (!pattern.matcher(model.getName()).matches()) {
                return false;
            }
        }

        // Filter by deprecated status
        if (filter.getIncludeDeprecated() != null && !filter.getIncludeDeprecated()) {
            if (Boolean.TRUE.equals(model.isDeprecated())) {
                return false;
            }
        }

        return true;
    }

    public static class Builder {
        private String baseUrl = "https://api.mistral.ai/v1";
        private String apiKey;
        private Duration timeout;
        private Boolean logRequests;
        private Boolean logResponses;
        private Logger logger;
        private HttpClientBuilder httpClientBuilder;

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public Builder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public Builder logger(Logger logger) {
            this.logger = logger;
            return this;
        }

        public Builder httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return this;
        }

        public MistralAiModelDiscovery build() {
            return new MistralAiModelDiscovery(this);
        }
    }
}
