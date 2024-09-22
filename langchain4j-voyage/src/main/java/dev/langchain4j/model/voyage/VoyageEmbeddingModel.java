package dev.langchain4j.model.voyage;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.DimensionAwareEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.model.voyage.VoyageApi.DEFAULT_BASE_URL;
import static dev.langchain4j.model.voyage.VoyageEmbeddingModelName.VOYAGE_3_LITE;
import static java.time.Duration.ofSeconds;
import static java.util.stream.Collectors.toList;

/**
 * An implementation of an {@link EmbeddingModel} that uses
 * <a href="https://docs.voyageai.com/docs/embeddings">Voyage AI Embedding API</a>.
 */
public class VoyageEmbeddingModel extends DimensionAwareEmbeddingModel {

    private final VoyageClient client;
    private final Integer maxRetries;
    private final VoyageEmbeddingModelName modelName;
    private final String inputType;
    private final Boolean truncation;
    private final String encodingFormat;
    private final int maxSegmentsPerBatch;

    public VoyageEmbeddingModel(
            String baseUrl,
            Duration timeout,
            Integer maxRetries,
            String apiKey,
            VoyageEmbeddingModelName modelName,
            String inputType,
            Boolean truncation,
            String encodingFormat,
            Boolean logRequests,
            Boolean logResponses,
            Integer maxSegmentsPerBatch
    ) {
        // Below attributes are force to non-null.
        this.maxRetries = getOrDefault(maxRetries, 3);
        this.modelName = getOrDefault(modelName, VOYAGE_3_LITE);
        this.truncation = getOrDefault(truncation, true);
        this.maxSegmentsPerBatch = getOrDefault(maxSegmentsPerBatch, 96);
        // Below attributes can be null.
        this.inputType = inputType;
        this.encodingFormat = encodingFormat;

        this.client = VoyageClient.builder()
                .baseUrl(getOrDefault(baseUrl, DEFAULT_BASE_URL))
                .apiKey(ensureNotBlank(apiKey, "apiKey"))
                .timeout(getOrDefault(timeout, ofSeconds(60)))
                .logRequests(getOrDefault(logRequests, false))
                .logResponses(getOrDefault(logResponses, false))
                .build();
    }

    public static VoyageEmbeddingModel withApiKey(String apiKey) {
        return VoyageEmbeddingModel.builder().apiKey(apiKey).build();
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
                    .input(batch)
                    .inputType(inputType)
                    .model(modelName.toString())
                    .truncation(truncation)
                    .encodingFormat(encodingFormat)
                    .build();

            EmbeddingResponse response = withRetry(() -> this.client.embed(request), maxRetries);

            embeddings.addAll(getEmbeddings(response));
            inputTokenCount += getTokenUsage(response);
        }

        return Response.from(
                embeddings,
                new TokenUsage(inputTokenCount)
        );

    }

    @Override
    protected Integer knownDimension() {
        return modelName.dimension();
    }

    private List<Embedding> getEmbeddings(EmbeddingResponse response) {
        return response.getData().stream()
                .sorted(Comparator.comparingInt(EmbeddingResponse.EmbeddingData::getIndex))
                .map(EmbeddingResponse.EmbeddingData::getEmbedding)
                .map(Embedding::from)
                .collect(toList());
    }

    private Integer getTokenUsage(EmbeddingResponse response) {
        if (response.getUsage() != null) {
            return response.getUsage().getTotalTokens();
        }
        return 0;
    }

    public static VoyageEmbeddingModelBuilder builder() {
        return new VoyageEmbeddingModelBuilder();
    }

    public static class VoyageEmbeddingModelBuilder {

        private String baseUrl;
        private Duration timeout;
        private Integer maxRetries;
        private String apiKey;
        private VoyageEmbeddingModelName modelName;
        private String inputType;
        private Boolean truncation;
        private String encodingFormat;
        private Boolean logRequests;
        private Boolean logResponses;
        private Integer maxSegmentsPerBatch;

        public VoyageEmbeddingModelBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public VoyageEmbeddingModelBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public VoyageEmbeddingModelBuilder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public VoyageEmbeddingModelBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public VoyageEmbeddingModelBuilder modelName(VoyageEmbeddingModelName modelName) {
            this.modelName = modelName;
            return this;
        }

        public VoyageEmbeddingModelBuilder inputType(String inputType) {
            this.inputType = inputType;
            return this;
        }

        public VoyageEmbeddingModelBuilder truncation(Boolean truncation) {
            this.truncation = truncation;
            return this;
        }

        public VoyageEmbeddingModelBuilder encodingFormat(String encodingFormat) {
            this.encodingFormat = encodingFormat;
            return this;
        }

        public VoyageEmbeddingModelBuilder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public VoyageEmbeddingModelBuilder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public VoyageEmbeddingModelBuilder maxSegmentsPerBatch(Integer maxSegmentsPerBatch) {
            this.maxSegmentsPerBatch = maxSegmentsPerBatch;
            return this;
        }

        public VoyageEmbeddingModel build() {
            return new VoyageEmbeddingModel(
                    baseUrl,
                    timeout,
                    maxRetries,
                    apiKey,
                    modelName,
                    inputType,
                    truncation,
                    encodingFormat,
                    logRequests,
                    logResponses,
                    maxSegmentsPerBatch
            );
        }
    }
}
