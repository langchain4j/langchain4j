package dev.langchain4j.model.ollama;

import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.model.embedding.DimensionAwareEmbeddingModel;
import dev.langchain4j.model.ollama.spi.OllamaEmbeddingModelBuilderFactory;
import dev.langchain4j.model.output.Response;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * <a href="https://github.com/jmorganca/ollama/blob/main/docs/api.md">Ollama API reference</a>
 */
public class OllamaEmbeddingModel extends DimensionAwareEmbeddingModel {

    private final OllamaClient client;
    private final String modelName;
    private final Integer maxRetries;

    public OllamaEmbeddingModel(OllamaEmbeddingModelBuilder builder) {
        this.client = OllamaClient.builder()
                .httpClientBuilder(builder.httpClientBuilder)
                .baseUrl(builder.baseUrl)
                .timeout(builder.timeout)
                .logRequests(builder.logRequests)
                .logResponses(builder.logResponses)
                .customHeaders(builder.customHeaders)
                .build();
        this.modelName = ensureNotBlank(builder.modelName, "modelName");
        this.maxRetries = getOrDefault(builder.maxRetries, 2);
    }

    public static OllamaEmbeddingModelBuilder builder() {
        for (OllamaEmbeddingModelBuilderFactory factory : loadFactories(OllamaEmbeddingModelBuilderFactory.class)) {
            return factory.get();
        }
        return new OllamaEmbeddingModelBuilder();
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
        List<String> input = textSegments.stream().map(TextSegment::text).collect(Collectors.toList());

        EmbeddingRequest request =
                EmbeddingRequest.builder().model(modelName).input(input).build();
        EmbeddingResponse response = withRetryMappingExceptions(() -> client.embed(request), maxRetries);
        List<Embedding> embeddings =
                response.getEmbeddings().stream().map(Embedding::from).collect(Collectors.toList());

        return Response.from(embeddings);
    }

    @Override
    public String modelName() {
        return this.modelName;
    }

    public static class OllamaEmbeddingModelBuilder {

        private HttpClientBuilder httpClientBuilder;
        private String baseUrl;
        private String modelName;
        private Duration timeout;
        private Integer maxRetries;
        private Boolean logRequests;
        private Boolean logResponses;
        private Map<String, String> customHeaders;

        public OllamaEmbeddingModelBuilder() {
            // This is public so it can be extended
        }

        /**
         * Sets the {@link HttpClientBuilder} that will be used to create the {@link HttpClient}
         * that will be used to communicate with Ollama.
         * <p>
         * NOTE: {@link #timeout(Duration)} overrides timeouts set on the {@link HttpClientBuilder}.
         */
        public OllamaEmbeddingModelBuilder httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return this;
        }

        public OllamaEmbeddingModelBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public OllamaEmbeddingModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public OllamaEmbeddingModelBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public OllamaEmbeddingModelBuilder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public OllamaEmbeddingModelBuilder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public OllamaEmbeddingModelBuilder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public OllamaEmbeddingModelBuilder customHeaders(Map<String, String> customHeaders) {
            this.customHeaders = customHeaders;
            return this;
        }

        public OllamaEmbeddingModel build() {
            return new OllamaEmbeddingModel(this);
        }
    }
}
