package dev.langchain4j.model.googleai;

import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.model.embedding.DimensionAwareEmbeddingModel;
import dev.langchain4j.model.googleai.GeminiEmbeddingRequestResponse.GeminiBatchEmbeddingRequest;
import dev.langchain4j.model.googleai.GeminiEmbeddingRequestResponse.GeminiBatchEmbeddingResponse;
import dev.langchain4j.model.googleai.GeminiEmbeddingRequestResponse.GeminiEmbeddingRequest;
import dev.langchain4j.model.googleai.GeminiEmbeddingRequestResponse.GeminiEmbeddingResponse;
import dev.langchain4j.model.output.Response;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;

public class GoogleAiEmbeddingModel extends DimensionAwareEmbeddingModel {

    private static final int MAX_NUMBER_OF_SEGMENTS_PER_BATCH = 100;

    private final GeminiService geminiService;
    private final String modelName;
    private final Integer maxRetries;
    private final TaskType taskType;
    private final String titleMetadataKey;
    private final Integer outputDimensionality;

    public GoogleAiEmbeddingModel(GoogleAiEmbeddingModelBuilder builder) {
        ensureNotBlank(builder.apiKey, "apiKey");
        this.geminiService = new GeminiService(
                builder.httpClientBuilder,
                builder.apiKey,
                builder.baseUrl,
                getOrDefault(builder.logRequestsAndResponses, false),
                getOrDefault(builder.logRequests, false),
                getOrDefault(builder.logResponses, false),
                builder.logger,
                builder.timeout);
        this.modelName = ensureNotBlank(builder.modelName, "modelName");
        this.maxRetries = getOrDefault(builder.maxRetries, 2);
        this.taskType = builder.taskType;
        this.titleMetadataKey = getOrDefault(builder.titleMetadataKey, "title");
        this.outputDimensionality = builder.outputDimensionality;
    }

    public static GoogleAiEmbeddingModelBuilder builder() {
        return new GoogleAiEmbeddingModelBuilder();
    }

    @Override
    public Response<Embedding> embed(TextSegment textSegment) {
        GeminiEmbeddingRequest embeddingRequest = getGoogleAiEmbeddingRequest(textSegment);

        GeminiEmbeddingResponse geminiResponse =
                withRetryMappingExceptions(() -> geminiService.embed(modelName, embeddingRequest), maxRetries);

        return Response.from(Embedding.from(geminiResponse.embedding().values()));
    }

    @Override
    public Response<Embedding> embed(String text) {
        return embed(TextSegment.from(text));
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
        List<GeminiEmbeddingRequest> embeddingRequests =
                textSegments.stream().map(this::getGoogleAiEmbeddingRequest).collect(Collectors.toList());

        List<Embedding> allEmbeddings = new ArrayList<>();
        int numberOfEmbeddings = embeddingRequests.size();
        int numberOfBatches = 1 + numberOfEmbeddings / MAX_NUMBER_OF_SEGMENTS_PER_BATCH;

        for (int i = 0; i < numberOfBatches; i++) {
            int startIndex = MAX_NUMBER_OF_SEGMENTS_PER_BATCH * i;
            int lastIndex = Math.min(startIndex + MAX_NUMBER_OF_SEGMENTS_PER_BATCH, numberOfEmbeddings);

            if (startIndex >= numberOfEmbeddings) break;

            GeminiBatchEmbeddingRequest batchEmbeddingRequest =
                    new GeminiBatchEmbeddingRequest(embeddingRequests.subList(startIndex, lastIndex));

            GeminiBatchEmbeddingResponse geminiResponse =
                    withRetryMappingExceptions(() -> geminiService.batchEmbed(modelName, batchEmbeddingRequest));

            allEmbeddings.addAll(geminiResponse.embeddings().stream()
                    .map(values -> Embedding.from(values.values()))
                    .toList());
        }

        return Response.from(allEmbeddings);
    }

    @Override
    public String modelName() {
        return this.modelName;
    }

    private GeminiEmbeddingRequest getGoogleAiEmbeddingRequest(TextSegment textSegment) {
        GeminiContent.GeminiPart geminiPart =
                GeminiContent.GeminiPart.builder().text(textSegment.text()).build();

        GeminiContent content = new GeminiContent(Collections.singletonList(geminiPart), null);

        String title = null;
        if (TaskType.RETRIEVAL_DOCUMENT.equals(this.taskType)) {
            if (textSegment.metadata() != null && textSegment.metadata().getString(this.titleMetadataKey) != null) {
                title = textSegment.metadata().getString(this.titleMetadataKey);
            }
        }

        return new GeminiEmbeddingRequest(
                "models/" + this.modelName, content, this.taskType, title, this.outputDimensionality);
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

    public static class GoogleAiEmbeddingModelBuilder
            extends BaseGoogleAiEmbeddingModelBuilder<GoogleAiEmbeddingModelBuilder> {
        public GoogleAiEmbeddingModel build() {
            return new GoogleAiEmbeddingModel(this);
        }
    }

    abstract static class BaseGoogleAiEmbeddingModelBuilder<B extends BaseGoogleAiEmbeddingModelBuilder<B>> {
        HttpClientBuilder httpClientBuilder;
        String modelName;
        String apiKey;
        String baseUrl;
        Integer maxRetries;
        TaskType taskType;
        String titleMetadataKey;
        Integer outputDimensionality;
        Duration timeout;
        Boolean logRequestsAndResponses;
        Boolean logRequests;
        Boolean logResponses;
        Logger logger;

        public B httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return builder();
        }

        @SuppressWarnings("unchecked")
        protected B builder() {
            return (B) this;
        }

        public B modelName(String modelName) {
            this.modelName = modelName;
            return builder();
        }

        public B apiKey(String apiKey) {
            this.apiKey = apiKey;
            return builder();
        }

        public B baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return builder();
        }

        public B maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return builder();
        }

        public B taskType(TaskType taskType) {
            this.taskType = taskType;
            return builder();
        }

        public B titleMetadataKey(String titleMetadataKey) {
            this.titleMetadataKey = titleMetadataKey;
            return builder();
        }

        public B outputDimensionality(Integer outputDimensionality) {
            this.outputDimensionality = outputDimensionality;
            return builder();
        }

        public B timeout(Duration timeout) {
            this.timeout = timeout;
            return builder();
        }

        public B logRequestsAndResponses(Boolean logRequestsAndResponses) {
            this.logRequestsAndResponses = logRequestsAndResponses;
            return builder();
        }

        public B logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return builder();
        }

        public B logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return builder();
        }

        /**
         * @param logger an alternate {@link Logger} to be used instead of the default one provided by Langchain4J for logging requests and responses.
         * @return {@code this}.
         */
        public B logger(Logger logger) {
            this.logger = logger;
            return builder();
        }
    }
}
