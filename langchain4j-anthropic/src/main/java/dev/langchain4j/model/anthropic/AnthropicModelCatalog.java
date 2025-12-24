package dev.langchain4j.model.anthropic;

import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.anthropic.internal.api.AnthropicModelInfo;
import dev.langchain4j.model.anthropic.internal.api.AnthropicModelsListResponse;
import dev.langchain4j.model.anthropic.internal.client.AnthropicClient;
import dev.langchain4j.model.catalog.ModelCatalog;
import dev.langchain4j.model.catalog.ModelDescription;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;

/**
 * Anthropic implementation of {@link ModelCatalog}.
 *
 * <p>Uses the Anthropic Models API to dynamically discover available models.
 *
 * <p>Example:
 * <pre>{@code
 * AnthropicModelCatalog catalog = AnthropicModelCatalog.builder()
 *     .apiKey(System.getenv("ANTHROPIC_API_KEY"))
 *     .build();
 *
 * List<ModelDescription> models = catalog.listModels();
 * }</pre>
 */
public class AnthropicModelCatalog implements ModelCatalog {

    private final AnthropicClient client;

    private AnthropicModelCatalog(Builder builder) {
        this.client = AnthropicClient.builder()
                .httpClientBuilder(builder.httpClientBuilder)
                .baseUrl(Utils.getOrDefault(builder.baseUrl, "https://api.anthropic.com/v1/"))
                .apiKey(builder.apiKey)
                .version(Utils.getOrDefault(builder.version, "2023-06-01"))
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
    public List<ModelDescription> listModels() {
        AnthropicModelsListResponse response = client.listModels();
        List<ModelDescription> models =
                response.data.stream().map(this::mapToModelDescription).collect(Collectors.toList());

        return models;
    }

    @Override
    public ModelProvider provider() {
        return ModelProvider.ANTHROPIC;
    }

    private ModelDescription mapToModelDescription(AnthropicModelInfo modelInfo) {
        ModelDescription.Builder builder =
                ModelDescription.builder().name(modelInfo.id).provider(ModelProvider.ANTHROPIC);

        // Use display_name if available.
        if (modelInfo.displayName != null && !modelInfo.displayName.isEmpty()) {
            builder.displayName(modelInfo.displayName);
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

    public static class Builder {
        private HttpClientBuilder httpClientBuilder;
        private String baseUrl;
        private String apiKey;
        private String version;
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

        public AnthropicModelCatalog build() {
            return new AnthropicModelCatalog(this);
        }
    }
}
