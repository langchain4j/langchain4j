package dev.langchain4j.model.jina;

import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static java.time.Duration.ofSeconds;
import static java.util.stream.Collectors.toList;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.DimensionAwareEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.jina.internal.api.JinaEmbeddingRequest;
import dev.langchain4j.model.jina.internal.api.JinaEmbeddingResponse;
import dev.langchain4j.model.jina.internal.client.JinaClient;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;

/**
 * An implementation of an {@link EmbeddingModel} that uses
 * <a href="https://jina.ai/embeddings">Jina Embeddings API</a>.
 */
public class JinaEmbeddingModel extends DimensionAwareEmbeddingModel {

    private static final String DEFAULT_BASE_URL = "https://api.jina.ai/";

    private final JinaClient client;
    private final String modelName;
    private final Integer maxRetries;
    private final Boolean lateChunking;

    @Deprecated(forRemoval = true, since = "1.4.0")
    public JinaEmbeddingModel(
            String baseUrl,
            String apiKey,
            String modelName,
            Duration timeout,
            Integer maxRetries,
            Boolean lateChunking,
            Boolean logRequests,
            Boolean logResponses) {
        this.client = JinaClient.builder()
                .baseUrl(getOrDefault(baseUrl, DEFAULT_BASE_URL))
                .apiKey(apiKey)
                .timeout(getOrDefault(timeout, ofSeconds(60)))
                .logRequests(getOrDefault(logRequests, false))
                .logResponses(getOrDefault(logResponses, false))
                .build();
        this.modelName = ensureNotBlank(modelName, "modelName");
        this.maxRetries = getOrDefault(maxRetries, 2);
        this.lateChunking = getOrDefault(lateChunking, false);
    }

    public JinaEmbeddingModel(JinaEmbeddingModelBuilder builder) {
        this.client = JinaClient.builder()
                .baseUrl(getOrDefault(builder.baseUrl, DEFAULT_BASE_URL))
                .apiKey(builder.apiKey)
                .timeout(getOrDefault(builder.timeout, ofSeconds(60)))
                .logRequests(getOrDefault(builder.logRequests, false))
                .logResponses(getOrDefault(builder.logResponses, false))
                .logger(builder.logger)
                .build();
        this.modelName = ensureNotBlank(builder.modelName, "modelName");
        this.maxRetries = getOrDefault(builder.maxRetries, 2);
        this.lateChunking = getOrDefault(builder.lateChunking, false);
    }

    public static JinaEmbeddingModelBuilder builder() {
        return new JinaEmbeddingModelBuilder();
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {

        JinaEmbeddingRequest request = JinaEmbeddingRequest.builder()
                .model(modelName)
                .lateChunking(lateChunking)
                .input(textSegments.stream().map(TextSegment::text).collect(toList()))
                .build();

        JinaEmbeddingResponse response = withRetryMappingExceptions(() -> client.embed(request), maxRetries);

        List<Embedding> embeddings = response.data == null
                ? List.of()
                : response.data.stream()
                        .map(jinaEmbedding -> Embedding.from(jinaEmbedding.embedding))
                        .collect(toList());

        TokenUsage tokenUsage = new TokenUsage(response.usage.promptTokens, 0, response.usage.totalTokens);
        return Response.from(embeddings, tokenUsage);
    }

    @Override
    public String modelName() {
        return this.modelName;
    }

    public static class JinaEmbeddingModelBuilder {
        private String baseUrl;
        private String apiKey;
        private String modelName;
        private Duration timeout;
        private Integer maxRetries;
        private Boolean lateChunking;
        private Boolean logRequests;
        private Boolean logResponses;
        private Logger logger;

        JinaEmbeddingModelBuilder() {}

        /**
         * Sets the base URL of the Jina API. Defaults to {@code "https://api.jina.ai/"}.
         *
         * @param baseUrl the base URL
         * @return {@code this}
         */
        public JinaEmbeddingModelBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /**
         * Sets the Jina API key used to authenticate requests.
         *
         * @param apiKey the Jina API key
         * @return {@code this}
         */
        public JinaEmbeddingModelBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * Sets the embedding model name, e.g. {@code "jina-embeddings-v3"}.
         *
         * @param modelName the model name
         * @return {@code this}
         */
        public JinaEmbeddingModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        /**
         * Sets the HTTP request timeout. Defaults to 60 seconds.
         *
         * @param timeout the request timeout
         * @return {@code this}
         */
        public JinaEmbeddingModelBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * Sets the maximum number of retries on transient errors. Defaults to {@code 2}.
         *
         * @param maxRetries the maximum number of retries
         * @return {@code this}
         */
        public JinaEmbeddingModelBuilder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        /**
         * Enables late chunking, which applies the model's context window over the entire input
         * and then performs chunking, improving embedding quality for segmented documents.
         * Defaults to {@code false}.
         *
         * @param lateChunking {@code true} to enable late chunking
         * @return {@code this}
         */
        public JinaEmbeddingModelBuilder lateChunking(Boolean lateChunking) {
            this.lateChunking = lateChunking;
            return this;
        }

        /**
         * Enables debug logging of request bodies sent to the Jina API.
         *
         * @param logRequests {@code true} to enable request logging
         * @return {@code this}
         */
        public JinaEmbeddingModelBuilder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        /**
         * Enables debug logging of response bodies received from the Jina API.
         *
         * @param logResponses {@code true} to enable response logging
         * @return {@code this}
         */
        public JinaEmbeddingModelBuilder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        /**
         * @param logger an alternate {@link Logger} to be used instead of the default one provided by Langchain4J for logging requests and responses.
         * @return {@code this}.
         */
        public JinaEmbeddingModelBuilder logger(Logger logger) {
            this.logger = logger;
            return this;
        }

        public JinaEmbeddingModel build() {
            return new JinaEmbeddingModel(this);
        }

        public String toString() {
            return "JinaEmbeddingModel.JinaEmbeddingModelBuilder(baseUrl=" + this.baseUrl + ", apiKey="
                    + (this.apiKey == null ? null : "********") + ", modelName=" + this.modelName + ", timeout="
                    + this.timeout + ", maxRetries=" + this.maxRetries + ", lateChunking=" + this.lateChunking
                    + ", logRequests=" + this.logRequests + ", logResponses=" + this.logResponses + ")";
        }
    }
}
