package dev.langchain4j.model.ollama;

import static dev.langchain4j.internal.CompletableFutureUtils.propagateCancellation;
import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptionsAsync;
import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;

import dev.langchain4j.Experimental;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.listener.DecisionModelListener;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.response.DecisionResponse;
import dev.langchain4j.model.ollama.spi.OllamaDecisionModelBuilderFactory;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.slf4j.Logger;

/**
 * A {@link DecisionModel} backed by the <a href="https://docs.ollama.com/capabilities/decision">decision models</a>
 * that Ollama runs locally, such as Nimble, Tev1 and Clef, through its
 * <a href="https://docs.ollama.com/api/systemone">System One API</a> ({@code POST /v1/systemone}, Ollama v0.35.0 or
 * later).
 * <p>
 * Download a decision model first, for example with {@code ollama pull nimble}, then:
 * <pre>{@code
 * DecisionModel model = OllamaDecisionModel.builder()
 *         .baseUrl("http://localhost:11434")
 *         .modelName("nimble")
 *         .build();
 * }</pre>
 * The input can be text, named fields (sent as a JSON object), or a list of
 * {@link dev.langchain4j.data.message.TextContent}s and {@link dev.langchain4j.data.message.ImageContent}s, or a
 * conversation (sent as a JSON transcript, {@code {"messages": [...]}}).
 * Images can be judged only by models with vision, such as Clef: they are sent alongside the input, which refers to
 * each of them by its position, for example {@code [image 1]}. Images are numbered from 1 in the order they appear
 * in the input, across all fields.
 * <p>
 * Images given by an {@code http}, {@code https} or {@code file} URL are downloaded or read by this class, on the
 * calling thread (also for {@link #decideAsync(DecisionRequest)}), before the request is sent. Do not pass URLs that
 * come from untrusted sources, such as users or retrieved content: download and check them yourself, and pass the
 * image as base64 data.
 *
 * @since 1.23.0
 */
@Experimental
public class OllamaDecisionModel implements DecisionModel {

    private final OllamaClient client;
    private final Integer maxRetries;
    private final Integer keepAlive;
    private final OllamaDecisionRequestParameters defaultRequestParameters;
    private final List<DecisionModelListener> listeners;

    public OllamaDecisionModel(OllamaDecisionModelBuilder builder) {
        this.client = OllamaClient.builder()
                .httpClientBuilder(builder.httpClientBuilder)
                .baseUrl(builder.baseUrl)
                .timeout(builder.timeout)
                .logRequests(getOrDefault(builder.logRequests, false))
                .logResponses(getOrDefault(builder.logResponses, false))
                .logger(builder.logger)
                .customHeaders(builder.customHeadersSupplier)
                .build();
        this.maxRetries = getOrDefault(builder.maxRetries, 2);
        this.keepAlive = builder.keepAlive;
        this.defaultRequestParameters = OllamaDecisionRequestParameters.builder()
                .modelName(builder.modelName)
                .customParameters(builder.customParameters)
                .build();
        this.listeners = copy(builder.listeners);
    }

    public static OllamaDecisionModelBuilder builder() {
        for (OllamaDecisionModelBuilderFactory factory : loadFactories(OllamaDecisionModelBuilderFactory.class)) {
            return factory.get();
        }
        return new OllamaDecisionModelBuilder();
    }

    @Override
    public DecisionResponse doDecide(DecisionRequest request) {
        OllamaDecisionRequest ollamaRequest = OllamaDecisionMapper.toOllamaRequest(request, keepAlive);
        OllamaDecisionResponse response = withRetryMappingExceptions(() -> client.decide(ollamaRequest), maxRetries);
        return OllamaDecisionMapper.toDecisionResponse(response, request);
    }

    @Override
    public CompletableFuture<DecisionResponse> doDecideAsync(DecisionRequest request) {
        OllamaDecisionRequest ollamaRequest = OllamaDecisionMapper.toOllamaRequest(request, keepAlive);
        CompletableFuture<OllamaDecisionResponse> responseFuture =
                withRetryMappingExceptionsAsync(() -> client.decideAsync(ollamaRequest), maxRetries);
        CompletableFuture<DecisionResponse> result =
                responseFuture.thenApply(response -> OllamaDecisionMapper.toDecisionResponse(response, request));
        propagateCancellation(result, responseFuture);
        return result;
    }

    @Override
    public OllamaDecisionRequestParameters defaultRequestParameters() {
        return defaultRequestParameters;
    }

    @Override
    public List<DecisionModelListener> listeners() {
        return listeners;
    }

    @Override
    public ModelProvider provider() {
        return ModelProvider.OLLAMA;
    }

    public static class OllamaDecisionModelBuilder {

        private HttpClientBuilder httpClientBuilder;
        private String baseUrl;
        private String modelName;
        private Duration timeout;
        private Integer maxRetries;
        private Integer keepAlive;
        private Boolean logRequests;
        private Boolean logResponses;
        private Logger logger;
        private Supplier<Map<String, String>> customHeadersSupplier;
        private Map<String, Object> customParameters;
        private List<DecisionModelListener> listeners;

        public OllamaDecisionModelBuilder() {
            // This is public so it can be extended
        }

        /**
         * Sets the {@link HttpClientBuilder} used to create the HTTP client that communicates with Ollama.
         * <p>
         * NOTE: {@link #timeout(Duration)} overrides timeouts set on the {@link HttpClientBuilder}.
         */
        public OllamaDecisionModelBuilder httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return this;
        }

        /**
         * The URL of the Ollama server, for example {@code http://localhost:11434}. Required.
         */
        public OllamaDecisionModelBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /**
         * The decision model to use when a request does not specify one, for example {@code nimble}. The model name
         * must be set either here or on each request, and the model must have been downloaded
         * ({@code ollama pull nimble}).
         */
        public OllamaDecisionModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        /**
         * The connect and read timeout. When not set, the timeouts of the {@link #httpClientBuilder(HttpClientBuilder)}
         * are used, or 15 seconds (connect) and 60 seconds (read). Without a GPU, or while a model is being loaded, a
         * request can take several seconds.
         */
        public OllamaDecisionModelBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * The number of retries after the first attempt, for transient errors such as timeouts and server errors.
         * Default value is 2.
         */
        public OllamaDecisionModelBuilder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        /**
         * How long, in seconds, Ollama keeps the model loaded in memory after a request. {@code 0} unloads it right
         * away, and a negative value keeps it loaded. When not set, the Ollama server's setting is used (5 minutes,
         * unless configured otherwise). Loading a decision model can take several seconds, so a longer value avoids
         * that delay when requests are infrequent.
         */
        public OllamaDecisionModelBuilder keepAlive(Integer keepAlive) {
            this.keepAlive = keepAlive;
            return this;
        }

        /**
         * Whether to log requests. Default value is {@code false}.
         */
        public OllamaDecisionModelBuilder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        /**
         * Whether to log responses. Default value is {@code false}.
         */
        public OllamaDecisionModelBuilder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        /**
         * An alternate {@link Logger} to be used instead of the default one for logging requests and responses.
         */
        public OllamaDecisionModelBuilder logger(Logger logger) {
            this.logger = logger;
            return this;
        }

        /**
         * Sets custom HTTP headers.
         */
        public OllamaDecisionModelBuilder customHeaders(Map<String, String> customHeaders) {
            this.customHeadersSupplier = () -> customHeaders;
            return this;
        }

        /**
         * Sets a supplier for custom HTTP headers. The supplier is called before each request, allowing dynamic
         * header values, for example OAuth2 tokens that expire and need refreshing.
         */
        public OllamaDecisionModelBuilder customHeaders(Supplier<Map<String, String>> customHeadersSupplier) {
            this.customHeadersSupplier = customHeadersSupplier;
            return this;
        }

        /**
         * Custom parameters added to the body of every request, for example to use a feature of the Ollama System One
         * API that this class does not support yet. A custom parameter with the same name as a parameter set by this
         * class replaces it. They can be replaced for a single request with
         * {@link OllamaDecisionRequestParameters.Builder#customParameters(Map)}.
         */
        public OllamaDecisionModelBuilder customParameters(Map<String, Object> customParameters) {
            this.customParameters = customParameters;
            return this;
        }

        /**
         * Sets the listeners that are notified of every request, response and error.
         */
        public OllamaDecisionModelBuilder listeners(List<DecisionModelListener> listeners) {
            this.listeners = listeners;
            return this;
        }

        public OllamaDecisionModelBuilder listeners(DecisionModelListener... listeners) {
            return listeners(List.of(listeners));
        }

        public OllamaDecisionModel build() {
            return new OllamaDecisionModel(this);
        }
    }
}
