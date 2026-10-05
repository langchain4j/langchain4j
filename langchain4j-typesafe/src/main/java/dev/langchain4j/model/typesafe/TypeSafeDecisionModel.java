package dev.langchain4j.model.typesafe;

import static dev.langchain4j.internal.CompletableFutureUtils.propagateCancellation;
import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptionsAsync;
import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.model.ModelProvider.TYPESAFE;

import dev.langchain4j.Experimental;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.listener.DecisionModelListener;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.DecisionRequestParameters;
import dev.langchain4j.model.decision.response.DecisionResponse;
import dev.langchain4j.model.typesafe.internal.TypeSafeClient;
import dev.langchain4j.model.typesafe.internal.TypeSafeMapper;
import dev.langchain4j.model.typesafe.internal.TypeSafeRequest;
import dev.langchain4j.model.typesafe.internal.TypeSafeResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.slf4j.Logger;

/**
 * A {@link DecisionModel} backed by the <a href="https://docs.typesafe.ai">TypeSafe System One API</a>
 * ({@code POST /v1/systemone}), which serves decision models such as Jev.
 * <p>
 * Other servers that implement the same API (for example OpenRouter, or self-hosted open models) can be used by
 * setting {@link TypeSafeDecisionModelBuilder#baseUrl(String)}.
 * <pre>{@code
 * DecisionModel model = TypeSafeDecisionModel.builder()
 *         .apiKey(System.getenv("TYPESAFE_API_KEY"))
 *         .modelName("jev-latest")
 *         .build();
 * }</pre>
 *
 * @since 1.21.0
 */
@Experimental
public class TypeSafeDecisionModel implements DecisionModel {

    private static final String DEFAULT_BASE_URL = "https://api.typesafe.ai";

    private final TypeSafeClient client;
    private final Integer maxRetries;
    private final DecisionRequestParameters defaultRequestParameters;
    private final List<DecisionModelListener> listeners;

    public TypeSafeDecisionModel(TypeSafeDecisionModelBuilder builder) {
        this.client = TypeSafeClient.builder()
                .httpClientBuilder(builder.httpClientBuilder)
                .baseUrl(getOrDefault(builder.baseUrl, DEFAULT_BASE_URL))
                .apiKey(apiKey(builder))
                .customHeaders(builder.customHeadersSupplier)
                .timeout(builder.timeout)
                .logRequests(getOrDefault(builder.logRequests, false))
                .logResponses(getOrDefault(builder.logResponses, false))
                .logger(builder.logger)
                .build();
        this.maxRetries = getOrDefault(builder.maxRetries, 2);
        this.defaultRequestParameters =
                DecisionRequestParameters.builder().modelName(builder.modelName).build();
        this.listeners = copy(builder.listeners);
    }

    private static String apiKey(TypeSafeDecisionModelBuilder builder) {
        boolean defaultBaseUrl = builder.baseUrl == null
                || DEFAULT_BASE_URL.equals(builder.baseUrl.replaceAll("/+$", ""));
        if (defaultBaseUrl) {
            return ensureNotBlank(builder.apiKey, "apiKey");
        }
        return isNullOrBlank(builder.apiKey) ? null : builder.apiKey;
    }

    public static TypeSafeDecisionModelBuilder builder() {
        return new TypeSafeDecisionModelBuilder();
    }

    @Override
    public DecisionResponse doDecide(DecisionRequest request) {
        TypeSafeRequest typeSafeRequest = TypeSafeMapper.toTypeSafeRequest(request);
        TypeSafeResponse response = withRetryMappingExceptions(() -> client.decide(typeSafeRequest), maxRetries);
        return TypeSafeMapper.toDecisionResponse(response, request);
    }

    @Override
    public CompletableFuture<DecisionResponse> doDecideAsync(DecisionRequest request) {
        TypeSafeRequest typeSafeRequest = TypeSafeMapper.toTypeSafeRequest(request);
        CompletableFuture<TypeSafeResponse> responseFuture =
                withRetryMappingExceptionsAsync(() -> client.decideAsync(typeSafeRequest), maxRetries);
        CompletableFuture<DecisionResponse> result =
                responseFuture.thenApply(response -> TypeSafeMapper.toDecisionResponse(response, request));
        propagateCancellation(result, responseFuture);
        return result;
    }

    @Override
    public DecisionRequestParameters defaultRequestParameters() {
        return defaultRequestParameters;
    }

    @Override
    public List<DecisionModelListener> listeners() {
        return listeners;
    }

    @Override
    public ModelProvider provider() {
        return TYPESAFE;
    }

    public static class TypeSafeDecisionModelBuilder {

        private HttpClientBuilder httpClientBuilder;
        private String baseUrl;
        private String apiKey;
        private String modelName;
        private Duration timeout;
        private Integer maxRetries;
        private Boolean logRequests;
        private Boolean logResponses;
        private Logger logger;
        private Supplier<Map<String, String>> customHeadersSupplier;
        private List<DecisionModelListener> listeners;

        /**
         * Sets a custom HTTP client builder, allowing fine-grained control over the HTTP client configuration such as
         * timeouts and proxy settings.
         */
        public TypeSafeDecisionModelBuilder httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return this;
        }

        /**
         * The base URL of the API. Defaults to {@code https://api.typesafe.ai}. Set it to use another server that
         * implements the System One API.
         */
        public TypeSafeDecisionModelBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /**
         * The API key. Required when using the default base URL; optional for other servers.
         */
        public TypeSafeDecisionModelBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * The model to use when a request does not specify one, for example {@code jev-latest}. The model name must
         * be set either here or on each request.
         */
        public TypeSafeDecisionModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        /**
         * The connect and read timeout. When not set, the timeouts of the {@link #httpClientBuilder(HttpClientBuilder)}
         * are used, or 15 seconds (connect) and 60 seconds (read).
         */
        public TypeSafeDecisionModelBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * The number of retries after the first attempt, for transient errors such as timeouts, rate limits and
         * server errors. Default value is 2.
         */
        public TypeSafeDecisionModelBuilder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        /**
         * Whether to log requests. Default value is {@code false}.
         */
        public TypeSafeDecisionModelBuilder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        /**
         * Whether to log responses. Default value is {@code false}.
         */
        public TypeSafeDecisionModelBuilder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        /**
         * An alternate {@link Logger} to be used instead of the default one for logging requests and responses.
         */
        public TypeSafeDecisionModelBuilder logger(Logger logger) {
            this.logger = logger;
            return this;
        }

        /**
         * Sets custom HTTP headers.
         */
        public TypeSafeDecisionModelBuilder customHeaders(Map<String, String> customHeaders) {
            this.customHeadersSupplier = () -> customHeaders;
            return this;
        }

        /**
         * Sets a supplier for custom HTTP headers. The supplier is called before each request, allowing dynamic
         * header values, for example OAuth2 tokens that expire and need refreshing.
         */
        public TypeSafeDecisionModelBuilder customHeaders(Supplier<Map<String, String>> customHeadersSupplier) {
            this.customHeadersSupplier = customHeadersSupplier;
            return this;
        }

        /**
         * Sets the listeners that are notified of every request, response and error.
         */
        public TypeSafeDecisionModelBuilder listeners(List<DecisionModelListener> listeners) {
            this.listeners = listeners;
            return this;
        }

        public TypeSafeDecisionModelBuilder listeners(DecisionModelListener... listeners) {
            return listeners(List.of(listeners));
        }

        public TypeSafeDecisionModel build() {
            return new TypeSafeDecisionModel(this);
        }
    }
}
