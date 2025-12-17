package dev.langchain4j.model.anthropic;

import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.discovery.ModelDescription;
import dev.langchain4j.model.discovery.ModelDiscovery;
import dev.langchain4j.model.discovery.ModelDiscoveryFilter;
import dev.langchain4j.model.anthropic.internal.api.AnthropicModelInfo;
import dev.langchain4j.model.anthropic.internal.api.AnthropicModelsListResponse;
import dev.langchain4j.model.anthropic.internal.client.AnthropicClient;
import org.slf4j.Logger;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Anthropic implementation of {@link ModelDiscovery}.
 *
 * <p>Uses the Anthropic Models API to dynamically discover available models.
 *
 * <p>Example:
 * <pre>{@code
 * AnthropicModelDiscovery discovery = AnthropicModelDiscovery.builder()
 *     .apiKey(System.getenv("ANTHROPIC_API_KEY"))
 *     .build();
 *
 * List<ModelDescription> models = discovery.discoverModels();
 * }</pre>
 */
public class AnthropicModelDiscovery implements ModelDiscovery {

    private final AnthropicClient client;

    private AnthropicModelDiscovery(Builder builder) {
        this.client = AnthropicClient.builder()
                .httpClientBuilder(builder.httpClientBuilder)
                .baseUrl(builder.baseUrl)
                .apiKey(builder.apiKey)
                .version(builder.version)
                .beta(builder.beta)
                .timeout(builder.timeout)
                .logRequests(builder.logRequests)
                .logResponses(builder.logResponses)
                .logger(builder.logger)
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
        AnthropicModelsListResponse response = client.listModels();
        List<ModelDescription> models = response.data.stream()
                .map(this::mapToModelDescription)
                .collect(Collectors.toList());

        // Anthropic doesn't support server-side filtering, so filter client-side
        if (filter != null && !filter.matchesAll()) {
            return filterModels(models, filter);
        }

        return models;
    }

    @Override
    public ModelProvider provider() {
        return ModelProvider.ANTHROPIC;
    }

    @Override
    public boolean supportsFiltering() {
        return false; // Anthropic doesn't support server-side filtering
    }

    private ModelDescription mapToModelDescription(AnthropicModelInfo modelInfo) {
        ModelDescription.Builder builder = ModelDescription.builder()
                .id(modelInfo.id)
                .provider(ModelProvider.ANTHROPIC);

        // Use display_name if available, otherwise use id
        if (modelInfo.displayName != null && !modelInfo.displayName.isEmpty()) {
            builder.name(modelInfo.displayName);
        } else {
            builder.name(modelInfo.id);
        }

        // Parse created_at timestamp if available
        if (modelInfo.createdAt != null) {
            try {
                Instant createdAt = Instant.from(DateTimeFormatter.ISO_INSTANT.parse(modelInfo.createdAt));
                builder.createdAt(createdAt);
            } catch (DateTimeParseException e) {
                // Ignore parsing errors and leave createdAt null
            }
        }

        return builder.build();
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
        private HttpClientBuilder httpClientBuilder;
        private String baseUrl = "https://api.anthropic.com/v1/";
        private String apiKey;
        private String version = "2023-06-01";
        private String beta;
        private Duration timeout;
        private Boolean logRequests;
        private Boolean logResponses;
        private Logger logger;

        public Builder httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return this;
        }

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder beta(String beta) {
            this.beta = beta;
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

        public AnthropicModelDiscovery build() {
            return new AnthropicModelDiscovery(this);
        }
    }
}
