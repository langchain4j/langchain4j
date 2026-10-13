package dev.langchain4j.model.openai;

import static dev.langchain4j.internal.CompletableFutureUtils.propagateCancellation;
import static dev.langchain4j.internal.Exceptions.unwrapCompletionException;
import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptionsAsync;
import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureGreaterThanZero;
import static dev.langchain4j.model.ModelProvider.OPEN_AI;
import static dev.langchain4j.model.openai.internal.OpenAiUtils.DEFAULT_OPENAI_URL;
import static dev.langchain4j.model.openai.internal.OpenAiUtils.DEFAULT_USER_AGENT;
import static dev.langchain4j.model.openai.internal.OpenAiUtils.tokenUsageFrom;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.time.Duration.ofSeconds;
import static java.util.Collections.unmodifiableMap;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.embedding.DimensionAwareEmbeddingModel;
import dev.langchain4j.model.embedding.listener.EmbeddingModelListener;
import dev.langchain4j.model.embedding.request.EmbeddingInput;
import dev.langchain4j.model.embedding.request.EmbeddingParameter;
import dev.langchain4j.model.embedding.request.EmbeddingRequest;
import dev.langchain4j.model.embedding.request.EmbeddingRequestParameters;
import dev.langchain4j.model.embedding.response.EmbeddingResponse;
import dev.langchain4j.model.embedding.response.EmbeddingResponseMetadata;
import dev.langchain4j.model.openai.internal.OpenAiClient;
import dev.langchain4j.model.openai.internal.ParsedAndRawResponse;
import dev.langchain4j.model.openai.spi.OpenAiEmbeddingModelBuilderFactory;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
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
    private final List<EmbeddingModelListener> listeners;

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
        this.listeners = copy(builder.listeners);
        ensureGreaterThanZero(this.maxSegmentsPerBatch, "maxSegmentsPerBatch");
    }

    @Override
    public List<EmbeddingModelListener> listeners() {
        return listeners;
    }

    @Override
    public ModelProvider provider() {
        return OPEN_AI;
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
    public Set<EmbeddingParameter<?>> supportedParameters() {
        return Set.of(
                EmbeddingRequestParameters.MODEL_NAME,
                EmbeddingRequestParameters.DIMENSIONS,
                OpenAiEmbeddingRequestParameters.USER,
                OpenAiEmbeddingRequestParameters.ENCODING_FORMAT,
                OpenAiEmbeddingRequestParameters.CUSTOM_PARAMETERS);
    }

    @Override
    public EmbeddingRequestParameters defaultRequestParameters() {
        return OpenAiEmbeddingRequestParameters.builder()
                .modelName(modelName)
                .dimensions(dimensions)
                .user(user)
                .encodingFormat(encodingFormat)
                .customParameters(customParameters)
                .build();
    }

    @Override
    public EmbeddingResponse doEmbed(EmbeddingRequest request) {

        EmbeddingRequestParameters parameters = request.parameters();

        List<String> texts = request.inputs().stream()
                .map(EmbeddingInput::text)
                .toList();
        List<List<String>> textBatches = partition(texts, maxSegmentsPerBatch);

        List<EmbeddedBatch> responses = new ArrayList<>();
        for (List<String> batch : textBatches) {
            responses.add(embedTexts(batch, parameters));
        }

        List<Embedding> embeddings =
                responses.stream().flatMap(batch -> batch.embeddings().stream()).toList();
        TokenUsage tokenUsage = responses.stream()
                .map(EmbeddedBatch::tokenUsage)
                .filter(Objects::nonNull)
                .reduce(TokenUsage::add)
                .orElse(null);
        String responseModelName = responses.stream()
                .map(EmbeddedBatch::modelName)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);

        return EmbeddingResponse.builder()
                .embeddings(embeddings)
                .metadata(EmbeddingResponseMetadata.builder()
                        .modelName(getOrDefault(responseModelName, getOrDefault(parameters.modelName(), modelName)))
                        .tokenUsage(tokenUsage)
                        .build())
                .build();
    }

    private record EmbeddedBatch(List<Embedding> embeddings, TokenUsage tokenUsage, String modelName) {}

    private List<List<String>> partition(List<String> inputList, int size) {
        List<List<String>> result = new ArrayList<>();
        for (int i = 0; i < inputList.size(); i += size) {
            int fromIndex = i;
            int toIndex = Math.min(i + size, inputList.size());
            result.add(inputList.subList(fromIndex, toIndex));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private EmbeddedBatch embedTexts(List<String> texts, EmbeddingRequestParameters parameters) {

        dev.langchain4j.model.openai.internal.embedding.EmbeddingRequest request =
                dev.langchain4j.model.openai.internal.embedding.EmbeddingRequest.builder()
                        .input(texts)
                        .model(parameters.modelName())
                        .dimensions(parameters.dimensions())
                        .user(parameters.parameter(OpenAiEmbeddingRequestParameters.USER))
                        .encodingFormat(parameters.parameter(OpenAiEmbeddingRequestParameters.ENCODING_FORMAT))
                        .customParameters(parameters.parameter(OpenAiEmbeddingRequestParameters.CUSTOM_PARAMETERS))
                        .build();

        dev.langchain4j.model.openai.internal.embedding.EmbeddingResponse response =
                withRetryMappingExceptions(() -> client.embedding(request).execute(), maxRetries);

        List<Embedding> embeddings = response.data().stream()
                .map(openAiEmbedding -> Embedding.from(openAiEmbedding.embedding()))
                .toList();

        return new EmbeddedBatch(embeddings, tokenUsageFrom(response.usage()), response.model());
    }

    /**
     * Genuinely non-blocking counterpart of {@link #doEmbed(EmbeddingRequest)}: each batch is sent over the async
     * HTTP path ({@code executeRawAsync}) and the per-batch futures are composed, so no thread is parked while the
     * OpenAI embeddings request is in flight. Cancelling the returned future aborts the in-flight HTTP request
     * (best-effort).
     * <p>
     * Like {@link #doEmbed(EmbeddingRequest)}, each batch request is retried up to {@code maxRetries} times
     * (retry-around-future, composing futures without parking a thread); a cancellation is never retried.
     */
    @Override
    public CompletableFuture<EmbeddingResponse> doEmbedAsync(EmbeddingRequest request) {

        EmbeddingRequestParameters parameters = request.parameters();

        List<String> texts = request.inputs().stream().map(EmbeddingInput::text).toList();
        List<List<String>> textBatches = partition(texts, maxSegmentsPerBatch);

        List<CompletableFuture<EmbeddedBatch>> batchFutures =
                textBatches.stream().map(batch -> embedTextsAsync(batch, parameters)).toList();

        CompletableFuture<EmbeddingResponse> aggregate = CompletableFuture.allOf(
                        batchFutures.toArray(new CompletableFuture[0]))
                .thenApply(ignored -> {
                    List<EmbeddedBatch> responses =
                            batchFutures.stream().map(CompletableFuture::join).toList();
                    List<Embedding> embeddings = responses.stream()
                            .flatMap(batch -> batch.embeddings().stream())
                            .toList();
                    TokenUsage tokenUsage = responses.stream()
                            .map(EmbeddedBatch::tokenUsage)
                            .filter(Objects::nonNull)
                            .reduce(TokenUsage::add)
                            .orElse(null);
                    String responseModelName = responses.stream()
                            .map(EmbeddedBatch::modelName)
                            .filter(Objects::nonNull)
                            .findFirst()
                            .orElse(null);
                    return EmbeddingResponse.builder()
                            .embeddings(embeddings)
                            .metadata(EmbeddingResponseMetadata.builder()
                                    .modelName(getOrDefault(
                                            responseModelName, getOrDefault(parameters.modelName(), modelName)))
                                    .tokenUsage(tokenUsage)
                                    .build())
                            .build();
                });

        CompletableFuture<EmbeddingResponse> result = new CompletableFuture<>();
        aggregate.whenComplete((response, error) -> {
            if (error == null) {
                result.complete(response);
            }
        });

        AtomicReference<Throwable> firstError = new AtomicReference<>();
        batchFutures.forEach(batchFuture -> batchFuture.whenComplete((response, error) -> {
            if (error != null) {
                Throwable cause = unwrapCompletionException(error);
                if (!(cause instanceof CancellationException) && firstError.compareAndSet(null, cause)) {
                    result.completeExceptionally(cause);
                    batchFutures.forEach(sibling -> sibling.cancel(true));
                }
            }
        }));
        batchFutures.forEach(batchFuture -> propagateCancellation(result, batchFuture));
        return result;
    }

    private CompletableFuture<EmbeddedBatch> embedTextsAsync(List<String> texts, EmbeddingRequestParameters parameters) {

        dev.langchain4j.model.openai.internal.embedding.EmbeddingRequest request =
                dev.langchain4j.model.openai.internal.embedding.EmbeddingRequest.builder()
                        .input(texts)
                        .model(parameters.modelName())
                        .dimensions(parameters.dimensions())
                        .user(parameters.parameter(OpenAiEmbeddingRequestParameters.USER))
                        .encodingFormat(parameters.parameter(OpenAiEmbeddingRequestParameters.ENCODING_FORMAT))
                        .customParameters(parameters.parameter(OpenAiEmbeddingRequestParameters.CUSTOM_PARAMETERS))
                        .build();

        CompletableFuture<ParsedAndRawResponse<dev.langchain4j.model.openai.internal.embedding.EmbeddingResponse>>
                rawFuture = withRetryMappingExceptionsAsync(
                        () -> client.embedding(request).executeRawAsync(), maxRetries);

        CompletableFuture<EmbeddedBatch> result = rawFuture.thenApply(parsedAndRaw -> {
            dev.langchain4j.model.openai.internal.embedding.EmbeddingResponse response = parsedAndRaw.parsedResponse();
            List<Embedding> embeddings = response.data().stream()
                    .map(openAiEmbedding -> Embedding.from(openAiEmbedding.embedding()))
                    .toList();
            return new EmbeddedBatch(embeddings, tokenUsageFrom(response.usage()), response.model());
        });

        propagateCancellation(result, rawFuture);
        return result;
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
        private List<EmbeddingModelListener> listeners;

        public OpenAiEmbeddingModelBuilder() {
            // This is public so it can be extended
        }

        public OpenAiEmbeddingModelBuilder listeners(List<EmbeddingModelListener> listeners) {
            this.listeners = listeners;
            return this;
        }

        public OpenAiEmbeddingModelBuilder httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return this;
        }

        public OpenAiEmbeddingModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public OpenAiEmbeddingModelBuilder modelName(OpenAiEmbeddingModelName modelName) {
            this.modelName = modelName.toString();
            return this;
        }

        public OpenAiEmbeddingModelBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public OpenAiEmbeddingModelBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public OpenAiEmbeddingModelBuilder organizationId(String organizationId) {
            this.organizationId = organizationId;
            return this;
        }

        public OpenAiEmbeddingModelBuilder projectId(String projectId) {
            this.projectId = projectId;
            return this;
        }

        public OpenAiEmbeddingModelBuilder dimensions(Integer dimensions) {
            this.dimensions = dimensions;
            return this;
        }

        public OpenAiEmbeddingModelBuilder user(String user) {
            this.user = user;
            return this;
        }

        public OpenAiEmbeddingModelBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public OpenAiEmbeddingModelBuilder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public OpenAiEmbeddingModelBuilder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

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

        public OpenAiEmbeddingModelBuilder customQueryParams(Map<String, String> customQueryParams) {
            this.customQueryParams = customQueryParams;
            return this;
        }

        public OpenAiEmbeddingModelBuilder maxSegmentsPerBatch(Integer maxSegmentsPerBatch) {
            this.maxSegmentsPerBatch = maxSegmentsPerBatch;
            return this;
        }

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
