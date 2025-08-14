package dev.langchain4j.model.jina;

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

import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static java.time.Duration.ofSeconds;
import static java.util.stream.Collectors.toList;

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

        List<Embedding> embeddings = response.data.stream()
                .map(jinaEmbedding -> Embedding.from(jinaEmbedding.embedding))
                .collect(toList());

        TokenUsage tokenUsage = new TokenUsage(response.usage.promptTokens, 0, response.usage.totalTokens);
        return Response.from(embeddings, tokenUsage);
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

        JinaEmbeddingModelBuilder() {
        }

        public JinaEmbeddingModelBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public JinaEmbeddingModelBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public JinaEmbeddingModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public JinaEmbeddingModelBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public JinaEmbeddingModelBuilder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public JinaEmbeddingModelBuilder lateChunking(Boolean lateChunking) {
            this.lateChunking = lateChunking;
            return this;
        }

        public JinaEmbeddingModelBuilder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public JinaEmbeddingModelBuilder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public JinaEmbeddingModel build() {
            return new JinaEmbeddingModel(this.baseUrl, this.apiKey, this.modelName, this.timeout, this.maxRetries, this.lateChunking, this.logRequests, this.logResponses);
        }

        public String toString() {
            return "JinaEmbeddingModel.JinaEmbeddingModelBuilder(baseUrl=" + this.baseUrl + ", apiKey=" + this.apiKey + ", modelName=" + this.modelName + ", timeout=" + this.timeout + ", maxRetries=" + this.maxRetries + ", lateChunking=" + this.lateChunking + ", logRequests=" + this.logRequests + ", logResponses=" + this.logResponses + ")";
        }
    }
}
