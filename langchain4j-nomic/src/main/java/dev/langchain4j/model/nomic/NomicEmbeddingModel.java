package dev.langchain4j.model.nomic;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.DimensionAwareEmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static java.time.Duration.ofSeconds;
import static java.util.stream.Collectors.toList;

/**
 * An integration with Nomic Atlas's Text Embeddings API.
 * See more details <a href="https://docs.nomic.ai/reference/endpoints/nomic-embed-text">here</a>.
 */
public class NomicEmbeddingModel extends DimensionAwareEmbeddingModel {

    private static final String DEFAULT_BASE_URL = "https://api-atlas.nomic.ai/v1/";

    private final NomicClient client;
    private final String modelName;
    private final String taskType;
    private final Integer maxSegmentsPerBatch;
    private final Integer maxRetries;

    public NomicEmbeddingModel(
            String baseUrl,
            String apiKey,
            String modelName,
            String taskType,
            Integer maxSegmentsPerBatch,
            Duration timeout,
            Integer maxRetries,
            Boolean logRequests,
            Boolean logResponses
    ) {
        this.client = NomicClient.builder()
                .baseUrl(getOrDefault(baseUrl, DEFAULT_BASE_URL))
                .apiKey(ensureNotBlank(apiKey, "apiKey"))
                .timeout(getOrDefault(timeout, ofSeconds(60)))
                .logRequests(getOrDefault(logRequests, false))
                .logResponses(getOrDefault(logResponses, false))
                .build();
        this.modelName = ensureNotBlank(modelName, "modelName");
        this.taskType = taskType;
        this.maxSegmentsPerBatch = getOrDefault(maxSegmentsPerBatch, 500);
        this.maxRetries = getOrDefault(maxRetries, 2);
    }

    public static NomicEmbeddingModelBuilder builder() {
        return new NomicEmbeddingModelBuilder();
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {

        List<String> texts = textSegments.stream()
                .map(TextSegment::text)
                .collect(toList());

        return embedTexts(texts);
    }

    private Response<List<Embedding>> embedTexts(List<String> texts) {

        List<Embedding> embeddings = new ArrayList<>();
        int inputTokenCount = 0;

        for (int i = 0; i < texts.size(); i += maxSegmentsPerBatch) {
            List<String> batch = texts.subList(i, Math.min(i + maxSegmentsPerBatch, texts.size()));

            EmbeddingRequest request = EmbeddingRequest.builder()
                    .model(modelName)
                    .texts(batch)
                    .taskType(taskType)
                    .build();

            EmbeddingResponse response = withRetryMappingExceptions(() -> this.client.embed(request), maxRetries);

            embeddings.addAll(getEmbeddings(response));
            inputTokenCount += getTokenUsage(response);
        }

        return Response.from(embeddings, new TokenUsage(inputTokenCount, 0));
    }

    private List<Embedding> getEmbeddings(EmbeddingResponse response) {
        return response.getEmbeddings().stream()
                .map(Embedding::from)
                .collect(toList());
    }

    private Integer getTokenUsage(EmbeddingResponse response) {
        if (response.getUsage() != null) {
            return response.getUsage().getTotalTokens();
        }
        return 0;
    }

    public static class NomicEmbeddingModelBuilder {
        private String baseUrl;
        private String apiKey;
        private String modelName;
        private String taskType;
        private Integer maxSegmentsPerBatch;
        private Duration timeout;
        private Integer maxRetries;
        private Boolean logRequests;
        private Boolean logResponses;

        NomicEmbeddingModelBuilder() {
        }

        public NomicEmbeddingModelBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public NomicEmbeddingModelBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public NomicEmbeddingModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public NomicEmbeddingModelBuilder taskType(String taskType) {
            this.taskType = taskType;
            return this;
        }

        public NomicEmbeddingModelBuilder maxSegmentsPerBatch(Integer maxSegmentsPerBatch) {
            this.maxSegmentsPerBatch = maxSegmentsPerBatch;
            return this;
        }

        public NomicEmbeddingModelBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public NomicEmbeddingModelBuilder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public NomicEmbeddingModelBuilder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public NomicEmbeddingModelBuilder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public NomicEmbeddingModel build() {
            return new NomicEmbeddingModel(this.baseUrl, this.apiKey, this.modelName, this.taskType, this.maxSegmentsPerBatch, this.timeout, this.maxRetries, this.logRequests, this.logResponses);
        }

        public String toString() {
            return "NomicEmbeddingModel.NomicEmbeddingModelBuilder(baseUrl=" + this.baseUrl + ", apiKey=" + this.apiKey + ", modelName=" + this.modelName + ", taskType=" + this.taskType + ", maxSegmentsPerBatch=" + this.maxSegmentsPerBatch + ", timeout=" + this.timeout + ", maxRetries=" + this.maxRetries + ", logRequests=" + this.logRequests + ", logResponses=" + this.logResponses + ")";
        }
    }
}
