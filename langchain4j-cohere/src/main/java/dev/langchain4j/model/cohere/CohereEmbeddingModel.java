package dev.langchain4j.model.cohere;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static java.time.Duration.ofSeconds;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toList;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.DimensionAwareEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;

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

    @Deprecated(forRemoval = true, since = "1.4.0")
    public CohereEmbeddingModel(
            String baseUrl,
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

    public CohereEmbeddingModel(CohereEmbeddingModelBuilder builder) {
        this.client = CohereClient.builder()
                .baseUrl(getOrDefault(builder.baseUrl, DEFAULT_BASE_URL))
                .apiKey(ensureNotBlank(builder.apiKey, "apiKey"))
                .timeout(getOrDefault(builder.timeout, ofSeconds(60)))
                .logRequests(getOrDefault(builder.logRequests, false))
                .logResponses(getOrDefault(builder.logResponses, false))
                .logger(builder.logger)
                .build();
        this.modelName = builder.modelName;
        this.inputType = builder.inputType;
        this.maxSegmentsPerBatch = getOrDefault(builder.maxSegmentsPerBatch, DEFAULT_MAX_SEGMENTS_PER_BATCH);
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

        List<String> texts = textSegments.stream().map(TextSegment::text).collect(toList());

        return embedTexts(texts);
    }

    @Override
    public String modelName() {
        return this.modelName;
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

        return Response.from(embeddings, new TokenUsage(totalTokenUsage, 0));
    }

    private static List<Embedding> getEmbeddings(EmbedResponse response) {
        return stream(response.getEmbeddings()).map(Embedding::from).collect(toList());
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
        private Logger logger;
        private Integer maxSegmentsPerBatch;

        CohereEmbeddingModelBuilder() {}

        /**
         * Sets the base URL of the Cohere API. Defaults to {@code "https://api.cohere.ai/v1/"}.
         *
         * @param baseUrl the base URL
         * @return {@code this}
         */
        public CohereEmbeddingModelBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /**
         * Sets the Cohere API key used to authenticate requests.
         *
         * @param apiKey the Cohere API key
         * @return {@code this}
         */
        public CohereEmbeddingModelBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * Sets the embedding model name, e.g. {@code "embed-english-v3.0"} or
         * {@code "embed-multilingual-v3.0"}.
         *
         * @param modelName the model name
         * @return {@code this}
         */
        public CohereEmbeddingModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        /**
         * Sets the intended use of the embeddings, which affects the model's output.
         * Common values: {@code "search_document"}, {@code "search_query"},
         * {@code "classification"}, {@code "clustering"}.
         *
         * @param inputType the input type
         * @return {@code this}
         */
        public CohereEmbeddingModelBuilder inputType(String inputType) {
            this.inputType = inputType;
            return this;
        }

        /**
         * Sets the HTTP request timeout. Defaults to 60 seconds.
         *
         * @param timeout the request timeout
         * @return {@code this}
         */
        public CohereEmbeddingModelBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * Enables debug logging of request bodies sent to the Cohere API.
         *
         * @param logRequests {@code true} to enable request logging
         * @return {@code this}
         */
        public CohereEmbeddingModelBuilder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        /**
         * Enables debug logging of response bodies received from the Cohere API.
         *
         * @param logResponses {@code true} to enable response logging
         * @return {@code this}
         */
        public CohereEmbeddingModelBuilder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        /**
         * @param logger an alternate {@link Logger} to be used instead of the default one provided by Langchain4J for logging requests and responses.
         * @return {@code this}.
         */
        public CohereEmbeddingModelBuilder logger(Logger logger) {
            this.logger = logger;
            return this;
        }

        /**
         * Sets the maximum number of text segments per batch request.
         * Defaults to {@code 96} (the Cohere API maximum).
         *
         * @param maxSegmentsPerBatch the maximum number of segments per batch
         * @return {@code this}
         */
        public CohereEmbeddingModelBuilder maxSegmentsPerBatch(Integer maxSegmentsPerBatch) {
            this.maxSegmentsPerBatch = maxSegmentsPerBatch;
            return this;
        }

        public CohereEmbeddingModel build() {
            return new CohereEmbeddingModel(this);
        }

        public String toString() {
            return "CohereEmbeddingModel.CohereEmbeddingModelBuilder(baseUrl=" + this.baseUrl + ", apiKey="
                    + this.apiKey + ", modelName=" + this.modelName + ", inputType=" + this.inputType + ", timeout="
                    + this.timeout + ", logRequests=" + this.logRequests + ", logResponses=" + this.logResponses
                    + ", maxSegmentsPerBatch=" + this.maxSegmentsPerBatch + ")";
        }
    }
}
