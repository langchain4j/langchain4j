package dev.langchain4j.model.openai;

import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureGreaterThanZero;
import static dev.langchain4j.model.openai.internal.OpenAiUtils.DEFAULT_OPENAI_URL;
import static dev.langchain4j.model.openai.internal.OpenAiUtils.DEFAULT_USER_AGENT;
import static dev.langchain4j.model.openai.internal.OpenAiUtils.tokenUsageFrom;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.time.Duration.ofSeconds;
import static java.util.Collections.unmodifiableMap;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.model.embedding.DimensionAwareEmbeddingModel;
import dev.langchain4j.model.openai.internal.OpenAiClient;
import dev.langchain4j.model.openai.internal.embedding.EmbeddingRequest;
import dev.langchain4j.model.openai.internal.embedding.EmbeddingResponse;
import dev.langchain4j.model.openai.spi.OpenAiEmbeddingModelBuilderFactory;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import org.slf4j.Logger;

/**
 * Represents an OpenAI embedding model, such as text-embedding-ada-002.
 */
public class OpenAiEmbeddingModel extends DimensionAwareEmbeddingModel {

    private final OpenAiClient client;
    private final String modelName;
    private final Integer dimensions;
    private final String user;
    private final Integer maxRetries;
    private final Integer maxSegmentsPerBatch;
    private final String encodingFormat;
    private final Map<String, Object> customParameters;

    public OpenAiEmbeddingModel(OpenAiEmbeddingModelBuilder builder) {

        this.client = OpenAiClient.builder()
                .httpClientBuilder(builder.httpClientBuilder)
                .baseUrl(getOrDefault(builder.baseUrl, DEFAULT_OPENAI_URL))
                .apiKey(builder.apiKey)
                .organizationId(builder.organizationId)
                .projectId(builder.projectId)
                .connectTimeout(getOrDefault(builder.timeout, ofSeconds(15)))
                .readTimeout(getOrDefault(builder.timeout, ofSeconds(60)))
                .logRequests(getOrDefault(builder.logRequests, false))
                .logResponses(getOrDefault(builder.logResponses, false))
                .logger(builder.logger)
                .userAgent(DEFAULT_USER_AGENT)
                .customHeaders(builder.customHeadersSupplier)
                .customQueryParams(builder.customQueryParams)
                .build();
        this.modelName = builder.modelName;
        this.dimensions = builder.dimensions;
        this.user = builder.user;
        this.maxRetries = getOrDefault(builder.maxRetries, 2);
        this.maxSegmentsPerBatch = getOrDefault(builder.maxSegmentsPerBatch, 2048);
        this.encodingFormat = builder.encodingFormat;
        this.customParameters = builder.customParameters == null
                ? null
                : unmodifiableMap(new LinkedHashMap<>(builder.customParameters));
        ensureGreaterThanZero(this.maxSegmentsPerBatch, "maxSegmentsPerBatch");
    }

    @Override
    protected Integer knownDimension() {
        if (dimensions != null) {
            return dimensions;
        }

        return OpenAiEmbeddingModelName.knownDimension(modelName());
    }

    @Override
    public String modelName() {
        return modelName;
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {

        List<String> texts = textSegments.stream().map(TextSegment::text).toList();

        List<List<String>> textBatches = partition(texts, maxSegmentsPerBatch);

        return embedBatchedTexts(textBatches);
    }

    private List<List<String>> partition(List<String> inputList, int size) {
        List<List<String>> result = new ArrayList<>();
        for (int i = 0; i < inputList.size(); i += size) {
            int fromIndex = i;
            int toIndex = Math.min(i + size, inputList.size());
            result.add(inputList.subList(fromIndex, toIndex));
        }
        return result;
    }

    private Response<List<Embedding>> embedBatchedTexts(List<List<String>> textBatches) {
        List<Response<List<Embedding>>> responses = new ArrayList<>();
        for (List<String> batch : textBatches) {
            Response<List<Embedding>> response = embedTexts(batch);
            responses.add(response);
        }
        return Response.from(
                responses.stream()
                        .flatMap(response -> response.content().stream())
                        .toList(),
                responses.stream()
                        .map(Response::tokenUsage)
                        .filter(Objects::nonNull)
                        .reduce(TokenUsage::add)
                        .orElse(null));
    }

    private Response<List<Embedding>> embedTexts(List<String> texts) {

        EmbeddingRequest request = EmbeddingRequest.builder()
                .input(texts)
                .model(modelName)
                .dimensions(dimensions)
                .user(user)
                .encodingFormat(encodingFormat)
                .customParameters(customParameters)
                .build();

        EmbeddingResponse response =
                withRetryMappingExceptions(() -> client.embedding(request).execute(), maxRetries);

        List<Embedding> embeddings = response.data().stream()
                .map(openAiEmbedding -> Embedding.from(openAiEmbedding.embedding()))
                .toList();

        return Response.from(embeddings, tokenUsageFrom(response.usage()));
    }

    public static OpenAiEmbeddingModelBuilder builder() {
        for (OpenAiEmbeddingModelBuilderFactory factory : loadFactories(OpenAiEmbeddingModelBuilderFactory.class)) {
            return factory.get();
        }
        return new OpenAiEmbeddingModelBuilder();
    }

    public static class OpenAiEmbeddingModelBuilder {

        private HttpClientBuilder httpClientBuilder;
        private String baseUrl;
        private String apiKey;
        private String organizationId;
        private String projectId;

        private String modelName;
        private Integer dimensions;
        private String user;
        private Duration timeout;
        private Integer maxRetries;
        private Integer maxSegmentsPerBatch;
        private Boolean logRequests;
        private Boolean logResponses;
        private Logger logger;
        private Supplier<Map<String, String>> customHeadersSupplier;
        private Map<String, String> customQueryParams;
        private String encodingFormat;
        private Map<String, Object> customParameters;

        public OpenAiEmbeddingModelBuilder() {
            // This is public so it can be extended
        }

        /**
         * Sets a custom {@link HttpClientBuilder} used to create the HTTP client.
         * Allows full control over timeouts, proxy settings, and other HTTP client options.
         *
         * @param httpClientBuilder the HTTP client builder
         * @return {@code this}
         */
        public OpenAiEmbeddingModelBuilder httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return this;
        }

        /**
         * Sets the model name, e.g. {@code "text-embedding-3-small"}.
         *
         * @param modelName the model name
         * @return {@code this}
         */
        public OpenAiEmbeddingModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        /**
         * Sets the model name using a {@link OpenAiEmbeddingModelName} enum constant.
         *
         * @param modelName the model name enum value
         * @return {@code this}
         */
        public OpenAiEmbeddingModelBuilder modelName(OpenAiEmbeddingModelName modelName) {
            this.modelName = modelName.toString();
            return this;
        }

        /**
         * Sets the base URL of the OpenAI-compatible API. Defaults to {@code https://api.openai.com/v1}.
         *
         * @param baseUrl the base URL
         * @return {@code this}
         */
        public OpenAiEmbeddingModelBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /**
         * Sets the OpenAI API key used to authenticate requests.
         *
         * @param apiKey the API key
         * @return {@code this}
         */
        public OpenAiEmbeddingModelBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * Sets the OpenAI organization ID sent with each request.
         *
         * @param organizationId the organization ID
         * @return {@code this}
         */
        public OpenAiEmbeddingModelBuilder organizationId(String organizationId) {
            this.organizationId = organizationId;
            return this;
        }

        /**
         * Sets the OpenAI project ID sent with each request.
         *
         * @param projectId the project ID
         * @return {@code this}
         */
        public OpenAiEmbeddingModelBuilder projectId(String projectId) {
            this.projectId = projectId;
            return this;
        }

        /**
         * Sets the number of dimensions for the output embedding vectors.
         * Only supported by {@code text-embedding-3} and later models.
         *
         * @param dimensions the number of output dimensions
         * @return {@code this}
         */
        public OpenAiEmbeddingModelBuilder dimensions(Integer dimensions) {
            this.dimensions = dimensions;
            return this;
        }

        /**
         * Sets a unique end-user identifier sent to OpenAI to help monitor and detect abuse.
         *
         * @param user the end-user identifier
         * @return {@code this}
         */
        public OpenAiEmbeddingModelBuilder user(String user) {
            this.user = user;
            return this;
        }

        /**
         * Sets the HTTP request timeout. Defaults to 15 seconds for connect and 60 seconds for read.
         *
         * @param timeout the request timeout
         * @return {@code this}
         */
        public OpenAiEmbeddingModelBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * Sets the maximum number of retries on transient errors. Defaults to {@code 2}.
         *
         * @param maxRetries the maximum number of retries
         * @return {@code this}
         */
        public OpenAiEmbeddingModelBuilder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        /**
         * Enables or disables logging of HTTP request bodies for debugging. Defaults to {@code false}.
         *
         * @param logRequests whether to log requests
         * @return {@code this}
         */
        public OpenAiEmbeddingModelBuilder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        /**
         * Enables or disables logging of HTTP response bodies for debugging. Defaults to {@code false}.
         *
         * @param logResponses whether to log responses
         * @return {@code this}
         */
        public OpenAiEmbeddingModelBuilder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        /**
         * @param logger an alternate {@link Logger} to be used instead of the default one provided by Langchain4J for logging requests and responses.
         * @return {@code this}.
         */
        public OpenAiEmbeddingModelBuilder logger(Logger logger) {
            this.logger = logger;
            return this;
        }

        /**
         * Sets custom HTTP headers.
         */
        public OpenAiEmbeddingModelBuilder customHeaders(Map<String, String> customHeaders) {
            this.customHeadersSupplier = () -> customHeaders;
            return this;
        }

        /**
         * Sets a supplier for custom HTTP headers.
         * The supplier is called before each request, allowing dynamic header values.
         * For example, this is useful for OAuth2 tokens that expire and need refreshing.
         */
        public OpenAiEmbeddingModelBuilder customHeaders(Supplier<Map<String, String>> customHeadersSupplier) {
            this.customHeadersSupplier = customHeadersSupplier;
            return this;
        }

        /**
         * Sets additional URL query parameters appended to every request.
         *
         * @param customQueryParams the query parameters map
         * @return {@code this}
         */
        public OpenAiEmbeddingModelBuilder customQueryParams(Map<String, String> customQueryParams) {
            this.customQueryParams = customQueryParams;
            return this;
        }

        /**
         * Sets the maximum number of text segments sent in a single embedding request. Defaults to {@code 2048}.
         *
         * @param maxSegmentsPerBatch the maximum batch size
         * @return {@code this}
         */
        public OpenAiEmbeddingModelBuilder maxSegmentsPerBatch(Integer maxSegmentsPerBatch) {
            this.maxSegmentsPerBatch = maxSegmentsPerBatch;
            return this;
        }

        /**
         * Sets the encoding format for the returned embeddings, e.g. {@code "float"} or {@code "base64"}.
         *
         * @param encodingFormat the encoding format
         * @return {@code this}
         */
        public OpenAiEmbeddingModelBuilder encodingFormat(String encodingFormat) {
            this.encodingFormat = encodingFormat;
            return this;
        }

        /**
         * Sets custom HTTP body parameters.
         */
        public OpenAiEmbeddingModelBuilder customParameters(Map<String, Object> customParameters) {
            this.customParameters = customParameters;
            return this;
        }

        public OpenAiEmbeddingModel build() {
            return new OpenAiEmbeddingModel(this);
        }
    }
}
