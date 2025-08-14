package dev.langchain4j.model.cohere;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.DimensionAwareEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static java.time.Duration.ofSeconds;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toList;

/**
 * An implementation of an {@link EmbeddingModel} that uses
 * <a href="https://docs.cohere.com/docs/embed">Cohere Embed API</a>.
 */
public class CohereEmbeddingModel extends DimensionAwareEmbeddingModel {

    private static final String DEFAULT_BASE_URL = "https://api.cohere.ai/v1/";
    private static final int DEFAULT_MAX_SEGMENTS_PER_BATCH = 96;

    private final CohereClient client;
    private final String modelName;
    private final String inputType;
    private final int maxSegmentsPerBatch;

    public CohereEmbeddingModel(String baseUrl,
                                String apiKey,
                                String modelName,
                                String inputType,
                                Duration timeout,
                                Boolean logRequests,
                                Boolean logResponses,
                                Integer maxSegmentsPerBatch) {
        this.client = CohereClient.builder()
                .baseUrl(getOrDefault(baseUrl, DEFAULT_BASE_URL))
                .apiKey(ensureNotBlank(apiKey, "apiKey"))
                .timeout(getOrDefault(timeout, ofSeconds(60)))
                .logRequests(getOrDefault(logRequests, false))
                .logResponses(getOrDefault(logResponses, false))
                .build();
        this.modelName = modelName;
        this.inputType = inputType;
        this.maxSegmentsPerBatch = getOrDefault(maxSegmentsPerBatch, DEFAULT_MAX_SEGMENTS_PER_BATCH);
    }

    /**
     * @deprecated Please use {@code builder()} instead, and explicitly set the model name and,
     * if necessary, other parameters.
     */
    @Deprecated(forRemoval = true)
    public static CohereEmbeddingModel withApiKey(String apiKey) {
        return builder().apiKey(apiKey).build();
    }

    public static CohereEmbeddingModelBuilder builder() {
        return new CohereEmbeddingModelBuilder();
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
        Integer totalTokenUsage = 0;

        for (int i = 0; i < texts.size(); i += maxSegmentsPerBatch) {

            List<String> batch = texts.subList(i, Math.min(i + maxSegmentsPerBatch, texts.size()));

            EmbedRequest request = EmbedRequest.builder()
                    .texts(batch)
                    .inputType(inputType)
                    .model(modelName)
                    .build();

            EmbedResponse response = this.client.embed(request);

            embeddings.addAll(getEmbeddings(response));
            totalTokenUsage += getTokenUsage(response);
        }

        return Response.from(
                embeddings,
                new TokenUsage(totalTokenUsage, 0)
        );

    }

    private static List<Embedding> getEmbeddings(EmbedResponse response) {
        return stream(response.getEmbeddings())
                .map(Embedding::from)
                .collect(toList());
    }

    private static Integer getTokenUsage(EmbedResponse response) {
        if (response.getMeta() != null
                && response.getMeta().getBilledUnits() != null
                && response.getMeta().getBilledUnits().getInputTokens() != null) {
            return response.getMeta().getBilledUnits().getInputTokens();
        }
        return 0;
    }

    public static class CohereEmbeddingModelBuilder {
        private String baseUrl;
        private String apiKey;
        private String modelName;
        private String inputType;
        private Duration timeout;
        private Boolean logRequests;
        private Boolean logResponses;
        private Integer maxSegmentsPerBatch;

        CohereEmbeddingModelBuilder() {
        }

        public CohereEmbeddingModelBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public CohereEmbeddingModelBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public CohereEmbeddingModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public CohereEmbeddingModelBuilder inputType(String inputType) {
            this.inputType = inputType;
            return this;
        }

        public CohereEmbeddingModelBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public CohereEmbeddingModelBuilder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public CohereEmbeddingModelBuilder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public CohereEmbeddingModelBuilder maxSegmentsPerBatch(Integer maxSegmentsPerBatch) {
            this.maxSegmentsPerBatch = maxSegmentsPerBatch;
            return this;
        }

        public CohereEmbeddingModel build() {
            return new CohereEmbeddingModel(this.baseUrl, this.apiKey, this.modelName, this.inputType, this.timeout, this.logRequests, this.logResponses, this.maxSegmentsPerBatch);
        }

        public String toString() {
            return "CohereEmbeddingModel.CohereEmbeddingModelBuilder(baseUrl=" + this.baseUrl + ", apiKey=" + this.apiKey + ", modelName=" + this.modelName + ", inputType=" + this.inputType + ", timeout=" + this.timeout + ", logRequests=" + this.logRequests + ", logResponses=" + this.logResponses + ", maxSegmentsPerBatch=" + this.maxSegmentsPerBatch + ")";
        }
    }
}
