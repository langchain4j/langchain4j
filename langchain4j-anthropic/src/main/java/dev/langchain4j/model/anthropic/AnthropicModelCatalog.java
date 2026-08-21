package dev.langchain4j.model.anthropic;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.model.ModelProvider.ANTHROPIC;

import dev.langchain4j.http.client.HttpClientBuilder;
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
                .baseUrl(getOrDefault(builder.baseUrl, "https://api.anthropic.com/v1/"))
                .apiKey(builder.apiKey)
                .version(getOrDefault(builder.version, "2023-06-01"))
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
                response.data.stream().map(this::mapToModelDescription).toList();
        return models;
    }

    private ModelDescription mapToModelDescription(AnthropicModelInfo modelInfo) {
        ModelDescription.Builder builder = ModelDescription.builder()
                .name(modelInfo.id)
                .provider(ANTHROPIC)
                .displayName(isNullOrBlank(modelInfo.displayName) ? null : modelInfo.displayName)
                .createdAt(modelInfo.createdAt != null ? parse(modelInfo.createdAt) : null);

        if (modelInfo.maxInputTokens != null) {
            builder.maxInputTokens(modelInfo.maxInputTokens);
        }
        if (modelInfo.maxOutputTokens != null) {
            builder.maxOutputTokens(modelInfo.maxOutputTokens);
        }

        return builder.build();
    }

    private static Instant parse(String createdAt) {
        try {
            return Instant.from(DateTimeFormatter.ISO_INSTANT.parse(createdAt));
        } catch (DateTimeParseException e) {
            // Ignore parsing errors and leave createdAt null
            return null;
        }
    }

    @Override
    public ModelProvider provider() {
        return ANTHROPIC;
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

        /**
         * Sets a custom {@link HttpClientBuilder} for the underlying HTTP client.
         * Use this to configure timeouts, proxies, or other HTTP-level settings.
         *
         * @param httpClientBuilder the HTTP client builder
         * @return {@code this}
         */
        public Builder httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return this;
        }

        /**
         * Sets the base URL of the Anthropic API.
         * <p>
         * Defaults to {@code https://api.anthropic.com/v1/}.
         *
         * @param baseUrl the base URL
         * @return {@code this}
         */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /**
         * Sets the Anthropic API key used to authenticate requests.
         * <p>
         * Alternatively, set the {@code ANTHROPIC_API_KEY} environment variable.
         *
         * @param apiKey the API key
         * @return {@code this}
         */
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * Sets the value of the {@code anthropic-version} request header.
         * <p>
         * Defaults to {@code 2023-06-01}.
         * See the <a href="https://docs.anthropic.com/en/api/versioning">Anthropic API versioning docs</a>.
         *
         * @param version the API version string
         * @return {@code this}
         */
        public Builder version(String version) {
            this.version = version;
            return this;
        }

        /**
         * Sets the HTTP request timeout for calls to the Anthropic API.
         *
         * @param timeout the request timeout
         * @return {@code this}
         */
        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * Enables debug logging of HTTP request bodies sent to the Anthropic API.
         *
         * @param logRequests whether to log requests
         * @return {@code this}
         */
        public Builder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        /**
         * Enables debug logging of HTTP response bodies received from the Anthropic API.
         *
         * @param logResponses whether to log responses
         * @return {@code this}
         */
        public Builder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        /**
         * Sets an alternate {@link Logger} to be used instead of the default one
         * provided by LangChain4j for logging requests and responses.
         *
         * @param logger the logger to use
         * @return {@code this}
         */
        public Builder logger(Logger logger) {
            this.logger = logger;
            return this;
        }

        public AnthropicModelCatalog build() {
            return new AnthropicModelCatalog(this);
        }
    }
}
