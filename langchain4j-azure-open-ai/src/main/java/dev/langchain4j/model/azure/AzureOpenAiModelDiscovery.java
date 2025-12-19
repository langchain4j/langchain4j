package dev.langchain4j.model.azure;

import static dev.langchain4j.http.client.HttpMethod.*;
import static java.time.Duration.*;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.HttpClientBuilderLoader;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.log.LoggingHttpClient;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.discovery.ModelDescription;
import dev.langchain4j.model.discovery.ModelDiscovery;

/**
 * Azure OpenAI implementation of {@link ModelDiscovery}.
 *
 * <p>Uses the Azure OpenAI Models API to dynamically discover available models.
 *
 * <p>Example:
 * <pre>{@code
 * AzureOpenAiModelDiscovery discovery = AzureOpenAiModelDiscovery.builder()
 *     .endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
 *     .apiKey(System.getenv("AZURE_OPENAI_KEY"))
 *     .build();
 *
 * List<ModelDescription> models = discovery.discoverModels();
 * }</pre>
 */
public class AzureOpenAiModelDiscovery implements ModelDiscovery {

    private static final Duration DEFAULT_TIMEOUT = ofSeconds(60);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final HttpClient httpClient;
    private final String endpoint;
    private final String apiKey;

    private AzureOpenAiModelDiscovery(Builder builder) {
        if (builder.endpoint == null || builder.endpoint.trim().isEmpty()) {
            throw new IllegalArgumentException("endpoint cannot be null or blank");
        }
        if (builder.apiKey == null || builder.apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("apiKey cannot be null or blank");
        }
        this.endpoint = builder.endpoint;
        this.apiKey = builder.apiKey;

        HttpClientBuilder httpClientBuilder = builder.httpClientBuilder != null
                ? builder.httpClientBuilder
                : HttpClientBuilderLoader.loadHttpClientBuilder();

        Duration timeout = builder.timeout != null ? builder.timeout : DEFAULT_TIMEOUT;
        HttpClient httpClient =
                httpClientBuilder.connectTimeout(timeout).readTimeout(timeout).build();

        if (builder.logRequestsAndResponses || builder.logRequests || builder.logResponses) {
            this.httpClient = new LoggingHttpClient(
                    httpClient,
                    builder.logRequestsAndResponses || builder.logRequests,
                    builder.logRequestsAndResponses || builder.logResponses,
                    builder.logger);
        } else {
            this.httpClient = httpClient;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public List<ModelDescription> discoverModels() {
        ModelsListResponse response = listModels();
        List<ModelDescription> models =
                response.data.stream().map(this::mapToModelDescription).collect(Collectors.toList());

        return models;
    }

    @Override
    public ModelProvider provider() {
        return ModelProvider.AZURE_OPEN_AI;
    }

    private ModelsListResponse listModels() {
        // Ensure endpoint doesn't have trailing slash
        String baseEndpoint = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
        String url = baseEndpoint + "/openai/v1/models";

        HttpRequest request = HttpRequest.builder()
                .method(GET)
                .url(url)
                .addHeader("api-key", apiKey)
                .build();

        try {
            String responseBody = httpClient.execute(request).body();
            return OBJECT_MAPPER.readValue(responseBody, ModelsListResponse.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to list Azure OpenAI models", e);
        }
    }

    private ModelDescription mapToModelDescription(ModelInfo modelInfo) {
        ModelDescription.Builder builder =
                ModelDescription.builder().name(modelInfo.id).provider(ModelProvider.AZURE_OPEN_AI);

        // Azure OpenAI uses id as name
        builder.displayName(modelInfo.id);

        // Map created timestamp if available
        if (modelInfo.created != null) {
            builder.createdAt(Instant.ofEpochSecond(modelInfo.created));
        }

        return builder.build();
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class ModelsListResponse {
        @JsonProperty("object")
        public String object;

        @JsonProperty("data")
        public List<ModelInfo> data;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class ModelInfo {
        @JsonProperty("id")
        public String id;

        @JsonProperty("object")
        public String object;

        @JsonProperty("created")
        public Long created;

        @JsonProperty("owned_by")
        public String ownedBy;
    }

    public static class Builder {
        private HttpClientBuilder httpClientBuilder;
        private String endpoint;
        private String apiKey;
        private Duration timeout;
        private boolean logRequestsAndResponses;
        private boolean logRequests;
        private boolean logResponses;
        private Logger logger;

        public Builder httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return this;
        }

        public Builder endpoint(String endpoint) {
            this.endpoint = endpoint;
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

        public Builder logRequestsAndResponses(boolean logRequestsAndResponses) {
            this.logRequestsAndResponses = logRequestsAndResponses;
            return this;
        }

        public Builder logRequests(boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public Builder logResponses(boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public Builder logger(Logger logger) {
            this.logger = logger;
            return this;
        }

        public AzureOpenAiModelDiscovery build() {
            return new AzureOpenAiModelDiscovery(this);
        }
    }
}
