package dev.langchain4j.model.openai;

import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.catalog.ModelCatalog;
import dev.langchain4j.model.catalog.ModelDescription;
import dev.langchain4j.model.openai.internal.OpenAiClient;
import dev.langchain4j.model.openai.internal.models.ModelsListResponse;
import dev.langchain4j.model.openai.internal.models.OpenAiModelInfo;
import org.slf4j.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static dev.langchain4j.model.ModelProvider.OPEN_AI;

/**
 * OpenAI implementation of {@link ModelCatalog}.
 *
 * <p>Example:
 * <pre>{@code
 * OpenAiModelCatalog catalog = OpenAiModelCatalog.builder()
 *     .apiKey(System.getenv("OPENAI_API_KEY"))
 *     .build();
 *
 * List<ModelDescription> models = catalog.listModels();
 * }</pre>
 */
public class OpenAiModelCatalog implements ModelCatalog {

    private final OpenAiClient client;

    private OpenAiModelCatalog(Builder builder) {
        this.client = OpenAiClient.builder()
                .httpClientBuilder(builder.httpClientBuilder)
                .baseUrl(Utils.getOrDefault(builder.baseUrl, "https://api.openai.com/v1/"))
                .apiKey(builder.apiKey)
                .organizationId(builder.organizationId)
                .projectId(builder.projectId)
                .connectTimeout(builder.connectTimeout)
                .readTimeout(builder.readTimeout)
                .userAgent(builder.userAgent)
                .logRequests(builder.logRequests)
                .logResponses(builder.logResponses)
                .logger(builder.logger)
                .customHeaders(builder.customHeaders)
                .customQueryParams(builder.customQueryParams)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public List<ModelDescription> listModels() {
        ModelsListResponse response = client.listModels().execute();
        List<ModelDescription> models =
                response.getData().stream().map(this::mapToModelDescription).collect(Collectors.toList());

        return models;
    }

    @Override
    public ModelProvider provider() {
        return OPEN_AI;
    }

    private ModelDescription mapToModelDescription(OpenAiModelInfo modelInfo) {
        return ModelDescription.builder()
                .name(modelInfo.id())
                .provider(OPEN_AI)
                .owner(modelInfo.ownedBy())
                .createdAt(modelInfo.created() != null ? Instant.ofEpochSecond(modelInfo.created()) : null)
                .build();
    }

    public static class Builder {

        private HttpClientBuilder httpClientBuilder;
        private String baseUrl;
        private String apiKey;
        private String organizationId;
        private String projectId;
        private Duration connectTimeout;
        private Duration readTimeout;
        private String userAgent;
        private Boolean logRequests;
        private Boolean logResponses;
        private Logger logger;
        private Map<String, String> customHeaders;
        private Map<String, String> customQueryParams;

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

        public Builder organizationId(String organizationId) {
            this.organizationId = organizationId;
            return this;
        }

        public Builder projectId(String projectId) {
            this.projectId = projectId;
            return this;
        }

        public Builder connectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
            return this;
        }

        public Builder readTimeout(Duration readTimeout) {
            this.readTimeout = readTimeout;
            return this;
        }

        public Builder userAgent(String userAgent) {
            this.userAgent = userAgent;
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

        public Builder customHeaders(Map<String, String> customHeaders) {
            this.customHeaders = customHeaders;
            return this;
        }

        public Builder customQueryParams(Map<String, String> customQueryParams) {
            this.customQueryParams = customQueryParams;
            return this;
        }

        public OpenAiModelCatalog build() {
            return new OpenAiModelCatalog(this);
        }
    }
}
