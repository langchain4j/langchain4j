package dev.langchain4j.model.voyageai;

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

import static dev.langchain4j.model.chat.policy.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.model.voyageai.VoyageAiApi.DEFAULT_BASE_URL;
import static java.time.Duration.ofSeconds;
import static java.util.stream.Collectors.toList;

/**
 * An implementation of an {@link EmbeddingModel} that uses
 * <a href="https://docs.voyageai.com/docs/embeddings">Voyage AI Embedding API</a>.
 */
public class VoyageAiEmbeddingModel extends DimensionAwareEmbeddingModel {

    private final VoyageAiClient client;
    private final Integer maxRetries;
    private final String modelName;
    private final String inputType;
    private final Boolean truncation;
    private final String encodingFormat;
    private final Integer maxSegmentsPerBatch;

    public VoyageAiEmbeddingModel(
            String baseUrl,
            Duration timeout,
            Integer maxRetries,
            String apiKey,
            String modelName,
            String inputType,
            Boolean truncation,
            String encodingFormat,
            Boolean logRequests,
            Boolean logResponses,
            Integer maxSegmentsPerBatch
    ) {
        this.maxRetries = getOrDefault(maxRetries, 3);
        this.modelName = ensureNotBlank(modelName, "modelName");
        this.maxSegmentsPerBatch = getOrDefault(maxSegmentsPerBatch, 128);
        this.truncation = truncation;
        this.inputType = inputType;
        this.encodingFormat = encodingFormat;

        this.client = VoyageAiClient.builder()
                .baseUrl(getOrDefault(baseUrl, DEFAULT_BASE_URL))
                .apiKey(ensureNotBlank(apiKey, "apiKey"))
                .timeout(getOrDefault(timeout, ofSeconds(60)))
                .logRequests(getOrDefault(logRequests, false))
                .logResponses(getOrDefault(logResponses, false))
                .build();
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
                    .model(modelName)
                    .truncation(truncation)
                    .encodingFormat(encodingFormat)
                    .build();

            EmbeddingResponse response = withRetry(() -> this.client.embed(request), maxRetries);

            embeddings.addAll(getEmbeddings(response));
            inputTokenCount += getTokenUsage(response);
        }

        return Response.from(embeddings, new TokenUsage(inputTokenCount));
    }

    @Override
    protected Integer knownDimension() {
        return VoyageAiEmbeddingModelName.knownDimension(modelName);
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

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String baseUrl;
        private Duration timeout;
        private Integer maxRetries;
        private String apiKey;
        private String modelName;
        private String inputType;
        private Boolean truncation;
        private String encodingFormat;
        private Boolean logRequests;
        private Boolean logResponses;
        private Integer maxSegmentsPerBatch;

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * Name of the model.
         *
         * @param modelName Name of the model.
         * @see VoyageAiEmbeddingModelName
         */
        public Builder modelName(VoyageAiEmbeddingModelName modelName) {
            this.modelName = modelName.toString();
            return this;
        }

        /**
         * Name of the model.
         *
         * @param modelName Name of the model.
         * @see VoyageAiEmbeddingModelName
         */
        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        /**
         * Type of the input text. Defaults to null. Other options: query, document.
         *
         * <ul>
         *     <li>query: Use this for search or retrieval queries. Voyage AI will prepend a prompt to optimize the embeddings for query use cases.</li>
         *     <li>document: Use this for documents or content that you want to be retrievable. Voyage AI will prepend a prompt to optimize the embeddings for document use cases.</li>
         *     <li>null (default): The input text will be directly encoded without any additional prompt.</li>
         * </ul>
         *
         * @param inputType Type of input text
         */
        public Builder inputType(String inputType) {
            this.inputType = inputType;
            return this;
        }

        /**
         * Whether to truncate the input texts to fit within the context length. Defaults to true.
         *
         * <ul>
         *     <li>If true, over-length input texts will be truncated to fit within the context length, before vectorized by the embedding model.</li>
         *     <li>If false, an error will be raised if any given text exceeds the context length.</li>
         * </ul>
         *
         * @param truncation Whether to truncate the input texts.
         */
        public Builder truncation(Boolean truncation) {
            this.truncation = truncation;
            return this;
        }

        /**
         * Format in which the embeddings are encoded. We support two options:
         *
         * <ul>
         *     <li>If not specified (defaults to null): the embeddings are represented as lists of floating-point numbers;</li>
         *     <li>base64: the embeddings are compressed to base64 encodings.</li>
         * </ul>
         *
         * @param encodingFormat Format in which the embeddings are encoded. Support format is "null" and "base64".
         */
        public Builder encodingFormat(String encodingFormat) {
            this.encodingFormat = encodingFormat;
            return this;
        }

        public Builder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public Builder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public Builder maxSegmentsPerBatch(Integer maxSegmentsPerBatch) {
            this.maxSegmentsPerBatch = maxSegmentsPerBatch;
            return this;
        }

        public VoyageAiEmbeddingModel build() {
            return new VoyageAiEmbeddingModel(
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
