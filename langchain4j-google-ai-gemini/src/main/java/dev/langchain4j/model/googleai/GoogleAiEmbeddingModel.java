package dev.langchain4j.model.googleai;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.model.embedding.DimensionAwareEmbeddingModel;
import dev.langchain4j.model.output.Response;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

public class GoogleAiEmbeddingModel extends DimensionAwareEmbeddingModel {

    private static final int MAX_NUMBER_OF_SEGMENTS_PER_BATCH = 100;

    private final GeminiService geminiService;
    private final String modelName;
    private final String apiKey;
    private final Integer maxRetries;
    private final TaskType taskType;
    private final String titleMetadataKey;
    private final Integer outputDimensionality;

    public GoogleAiEmbeddingModel(GoogleAiEmbeddingModelBuilder builder) {
        this.geminiService = new GeminiService(
                builder.httpClientBuilder,
                getOrDefault(builder.logRequestsAndResponses, false),
                builder.timeout
        );
        this.modelName = ensureNotBlank(builder.modelName, "modelName");
        this.apiKey = ensureNotBlank(builder.apiKey, "apiKey");
        this.maxRetries = getOrDefault(builder.maxRetries, 2);
        this.taskType = builder.taskType;
        this.titleMetadataKey = getOrDefault(builder.titleMetadataKey, "title");
        this.outputDimensionality = builder.outputDimensionality;
    }

    /**
     * @deprecated please use {@link #GoogleAiEmbeddingModel(GoogleAiEmbeddingModelBuilder)} instead
     */
    @Deprecated(forRemoval = true, since = "1.1.0-beta7")
    public GoogleAiEmbeddingModel(
            String modelName,
            String apiKey,
            Integer maxRetries,
            TaskType taskType,
            String titleMetadataKey,
            Integer outputDimensionality,
            Duration timeout,
            Boolean logRequestsAndResponses
    ) {
        this.modelName = ensureNotBlank(modelName, "modelName");
        this.apiKey = ensureNotBlank(apiKey, "apiKey");

        this.maxRetries = getOrDefault(maxRetries, 2);

        this.taskType = taskType;
        this.titleMetadataKey = getOrDefault(titleMetadataKey, "title");

        this.outputDimensionality = outputDimensionality;

        this.geminiService = new GeminiService(
                null,
                getOrDefault(logRequestsAndResponses, false),
                timeout
        );
    }

    public static GoogleAiEmbeddingModelBuilder builder() {
        return new GoogleAiEmbeddingModelBuilder();
    }

    @Override
    public Response<Embedding> embed(TextSegment textSegment) {
        GoogleAiEmbeddingRequest embeddingRequest = getGoogleAiEmbeddingRequest(textSegment);

        GoogleAiEmbeddingResponse geminiResponse = withRetryMappingExceptions(() ->
                geminiService.embed(modelName, apiKey, embeddingRequest), maxRetries);

        return Response.from(Embedding.from(geminiResponse.getEmbedding().getValues()));
    }

    @Override
    public Response<Embedding> embed(String text) {
        return embed(TextSegment.from(text));
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
        List<GoogleAiEmbeddingRequest> embeddingRequests = textSegments.stream()
                .map(this::getGoogleAiEmbeddingRequest)
                .collect(Collectors.toList());

        List<Embedding> allEmbeddings = new ArrayList<>();
        int numberOfEmbeddings = embeddingRequests.size();
        int numberOfBatches = 1 + numberOfEmbeddings / MAX_NUMBER_OF_SEGMENTS_PER_BATCH;

        for (int i = 0; i < numberOfBatches; i++) {
            int startIndex = MAX_NUMBER_OF_SEGMENTS_PER_BATCH * i;
            int lastIndex = Math.min(startIndex + MAX_NUMBER_OF_SEGMENTS_PER_BATCH, numberOfEmbeddings);

            if (startIndex >= numberOfEmbeddings) break;

            GoogleAiBatchEmbeddingRequest batchEmbeddingRequest = new GoogleAiBatchEmbeddingRequest();
            batchEmbeddingRequest.setRequests(embeddingRequests.subList(startIndex, lastIndex));

            GoogleAiBatchEmbeddingResponse geminiResponse = withRetryMappingExceptions(() ->
                    geminiService.batchEmbed(modelName, apiKey, batchEmbeddingRequest));

            allEmbeddings.addAll(geminiResponse.getEmbeddings().stream()
                    .map(values -> Embedding.from(values.getValues()))
                    .toList());
        }

        return Response.from(allEmbeddings);
    }

    private GoogleAiEmbeddingRequest getGoogleAiEmbeddingRequest(TextSegment textSegment) {
        GeminiPart geminiPart = GeminiPart.builder()
                .text(textSegment.text())
                .build();

        GeminiContent content = new GeminiContent(Collections.singletonList(geminiPart), null);

        String title = null;
        if (TaskType.RETRIEVAL_DOCUMENT.equals(this.taskType)) {
            if (textSegment.metadata() != null && textSegment.metadata().getString(this.titleMetadataKey) != null) {
                title = textSegment.metadata().getString(this.titleMetadataKey);
            }
        }

        return new GoogleAiEmbeddingRequest(
                "models/" + this.modelName,
                content,
                this.taskType,
                title,
                this.outputDimensionality
        );
    }

    @Override
    public Integer knownDimension() {
        return outputDimensionality;
    }

    public enum TaskType {
        RETRIEVAL_QUERY,
        RETRIEVAL_DOCUMENT,
        SEMANTIC_SIMILARITY,
        CLASSIFICATION,
        CLUSTERING,
        QUESTION_ANSWERING,
        FACT_VERIFICATION
    }

    public static class GoogleAiEmbeddingModelBuilder {

        private HttpClientBuilder httpClientBuilder;
        private String modelName;
        private String apiKey;
        private Integer maxRetries;
        private TaskType taskType;
        private String titleMetadataKey;
        private Integer outputDimensionality;
        private Duration timeout;
        private Boolean logRequestsAndResponses;

        GoogleAiEmbeddingModelBuilder() {
        }

        public GoogleAiEmbeddingModelBuilder httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return this;
        }

        public GoogleAiEmbeddingModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public GoogleAiEmbeddingModelBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public GoogleAiEmbeddingModelBuilder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public GoogleAiEmbeddingModelBuilder taskType(TaskType taskType) {
            this.taskType = taskType;
            return this;
        }

        public GoogleAiEmbeddingModelBuilder titleMetadataKey(String titleMetadataKey) {
            this.titleMetadataKey = titleMetadataKey;
            return this;
        }

        public GoogleAiEmbeddingModelBuilder outputDimensionality(Integer outputDimensionality) {
            this.outputDimensionality = outputDimensionality;
            return this;
        }

        public GoogleAiEmbeddingModelBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public GoogleAiEmbeddingModelBuilder logRequestsAndResponses(Boolean logRequestsAndResponses) {
            this.logRequestsAndResponses = logRequestsAndResponses;
            return this;
        }

        public GoogleAiEmbeddingModel build() {
            return new GoogleAiEmbeddingModel(this);
        }
    }
}
