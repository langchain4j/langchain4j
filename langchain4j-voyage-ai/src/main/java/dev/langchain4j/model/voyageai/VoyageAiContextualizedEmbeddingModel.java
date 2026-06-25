package dev.langchain4j.model.voyageai;

import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.model.voyageai.VoyageAiClient.DEFAULT_BASE_URL;
import static java.time.Duration.ofSeconds;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.model.embedding.DimensionAwareEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.slf4j.Logger;

/**
 * An implementation of an {@link EmbeddingModel} that uses the
 * <a href="https://docs.voyageai.com/docs/contextualized-chunk-embeddings">Voyage AI Contextualized Chunk Embeddings API</a>.
 *
 * <p>Unlike {@link VoyageAiEmbeddingModel}, which embeds each text independently, this model embeds the
 * provided text segments as chunks of a <b>single document</b>, so each chunk embedding is influenced by the
 * other chunks. It targets the {@code /v1/contextualizedembeddings} endpoint with models such as
 * {@code voyage-context-4}.</p>
 */
public class VoyageAiContextualizedEmbeddingModel extends DimensionAwareEmbeddingModel {

    private final VoyageAiClient client;
    private final Integer maxRetries;
    private final String modelName;
    private final String inputType;
    private final Boolean truncation;
    private final String encodingFormat;

    public VoyageAiContextualizedEmbeddingModel(Builder builder) {
        this.maxRetries = getOrDefault(builder.maxRetries, 2);
        this.modelName = ensureNotBlank(builder.modelName, "modelName");
        this.truncation = builder.truncation;
        this.inputType = builder.inputType;
        this.encodingFormat = builder.encodingFormat;

        this.client = VoyageAiClient.builder()
                .httpClientBuilder(builder.httpClientBuilder)
                .baseUrl(getOrDefault(builder.baseUrl, DEFAULT_BASE_URL))
                .apiKey(ensureNotBlank(builder.apiKey, "apiKey"))
                .timeout(getOrDefault(builder.timeout, ofSeconds(60)))
                .logRequests(getOrDefault(builder.logRequests, false))
                .logResponses(getOrDefault(builder.logResponses, false))
                .logger(builder.logger)
                .customHeaders(builder.customHeadersSupplier)
                .build();
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
        List<String> texts = textSegments.stream().map(TextSegment::text).collect(toList());

        ContextualizedEmbeddingRequest request = ContextualizedEmbeddingRequest.builder()
                .inputs(singletonList(texts))
                .inputType(inputType)
                .model(modelName)
                .truncation(truncation)
                .encodingFormat(encodingFormat)
                .build();

        ContextualizedEmbeddingResponse response =
                withRetryMappingExceptions(() -> this.client.contextualizedEmbed(request), maxRetries);

        return Response.from(getEmbeddings(response), new TokenUsage(getTokenUsage(response)));
    }

    @Override
    public String modelName() {
        return this.modelName;
    }

    @Override
    protected Integer knownDimension() {
        return VoyageAiEmbeddingModelName.knownDimension(modelName);
    }

    private List<Embedding> getEmbeddings(ContextualizedEmbeddingResponse response) {
        return response.getData().stream()
                .sorted(Comparator.comparingInt(ContextualizedEmbeddingResponse.DocumentData::getIndex))
                .flatMap(documentData -> documentData.getData().stream()
                        .sorted(Comparator.comparingInt(EmbeddingResponse.EmbeddingData::getIndex)))
                .map(EmbeddingResponse.EmbeddingData::getEmbedding)
                .map(Embedding::from)
                .collect(toList());
    }

    private Integer getTokenUsage(ContextualizedEmbeddingResponse response) {
        if (response.getUsage() != null) {
            return response.getUsage().getTotalTokens();
        }
        return 0;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private HttpClientBuilder httpClientBuilder;
        private Supplier<Map<String, String>> customHeadersSupplier;
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
        private Logger logger;

        /**
         * Sets a custom HTTP client builder, allowing fine-grained control over the HTTP client
         * configuration such as timeouts and proxy settings.
         *
         * @param httpClientBuilder the HTTP client builder
         * @return {@code this}
         */
        public Builder httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return this;
        }

        /**
         * Sets custom HTTP headers.
         */
        public Builder customHeaders(Map<String, String> customHeaders) {
            this.customHeadersSupplier = () -> customHeaders;
            return this;
        }

        /**
         * Sets a supplier for custom HTTP headers.
         * The supplier is called before each request, allowing dynamic header values.
         * For example, this is useful for OAuth2 tokens that expire and need refreshing.
         */
        public Builder customHeaders(Supplier<Map<String, String>> customHeadersSupplier) {
            this.customHeadersSupplier = customHeadersSupplier;
            return this;
        }

        /**
         * Sets the base URL of the Voyage AI API.
         * Defaults to {@code "https://api.voyageai.com/v1/"}.
         *
         * @param baseUrl the base URL
         * @return {@code this}
         */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /**
         * Sets the HTTP request timeout. Defaults to 60 seconds.
         *
         * @param timeout the request timeout
         * @return {@code this}
         */
        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * Sets the maximum number of retries on transient errors. Defaults to {@code 2}.
         *
         * @param maxRetries the maximum number of retries
         * @return {@code this}
         */
        public Builder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        /**
         * Sets the Voyage AI API key used to authenticate requests.
         * See <a href="https://dash.voyageai.com/api-keys">Voyage AI dashboard</a> to obtain a key.
         *
         * @param apiKey the Voyage AI API key
         * @return {@code this}
         */
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

        /**
         * Enables debug logging of request bodies sent to the Voyage AI API.
         *
         * @param logRequests {@code true} to enable request logging
         * @return {@code this}
         */
        public Builder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        /**
         * Enables debug logging of response bodies received from the Voyage AI API.
         *
         * @param logResponses {@code true} to enable response logging
         * @return {@code this}
         */
        public Builder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        /**
         * @param logger an alternate {@link Logger} to be used instead of the default one provided by Langchain4J for logging requests and responses.
         * @return {@code this}.
         */
        public Builder logger(Logger logger) {
            this.logger = logger;
            return this;
        }

        public VoyageAiContextualizedEmbeddingModel build() {
            return new VoyageAiContextualizedEmbeddingModel(this);
        }
    }
}
