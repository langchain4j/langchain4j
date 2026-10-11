package dev.langchain4j.model.openai;

import static dev.langchain4j.internal.CompletableFutureUtils.propagateCancellation;
import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptionsAsync;
import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.model.openai.internal.OpenAiUtils.DEFAULT_OPENAI_URL;
import static dev.langchain4j.model.openai.internal.OpenAiUtils.DEFAULT_USER_AGENT;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.time.Duration.ofSeconds;

import dev.langchain4j.Experimental;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.listener.DecisionModelListener;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.DecisionRequestParameters;
import dev.langchain4j.model.decision.response.DecisionResponse;
import dev.langchain4j.model.openai.internal.OpenAiClient;
import dev.langchain4j.model.openai.internal.ParsedAndRawResponse;
import dev.langchain4j.model.openai.internal.decision.DecisionCreateRequest;
import dev.langchain4j.model.openai.internal.decision.DecisionCreateResponse;
import dev.langchain4j.model.openai.internal.decision.DecisionMapper;
import dev.langchain4j.model.openai.spi.OpenAiDecisionModelBuilderFactory;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.slf4j.Logger;

/**
 * A {@link DecisionModel} backed by the <a href="https://developers.openai.com/api/docs/guides/decisions">OpenAI
 * Decisions API</a> ({@code POST /v1/decisions}), which answers yes/no, choice and scale questions about a text or
 * image input with probabilities.
 * <pre>{@code
 * DecisionModel model = OpenAiDecisionModel.builder()
 *         .apiKey(System.getenv("OPENAI_API_KEY"))
 *         .modelName(OpenAiDecisionModelName.GPT_6_LUNA)
 *         .build();
 * }</pre>
 * The input can be text, a {@link java.util.Map} (sent as JSON text), or a list of
 * {@link dev.langchain4j.data.message.TextContent}s and {@link dev.langchain4j.data.message.ImageContent}s. Images
 * must be inline (base64 data or data URLs): images referenced by URL are not supported by the API. The image detail
 * levels {@code LOW}, {@code HIGH} and {@code AUTO} are supported.
 * <p>
 * The API can refuse to answer a single question, for example because it goes against the usage policies of
 * OpenAI: the answer to that question is then a {@link dev.langchain4j.model.decision.response.RefusalAnswer}.
 *
 * @since 1.22.0
 */
@Experimental
public class OpenAiDecisionModel implements DecisionModel {

    private final OpenAiClient client;
    private final Integer maxRetries;
    private final DecisionRequestParameters defaultRequestParameters;
    private final List<DecisionModelListener> listeners;

    public OpenAiDecisionModel(OpenAiDecisionModelBuilder builder) {
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
        this.maxRetries = getOrDefault(builder.maxRetries, 2);
        this.defaultRequestParameters =
                DecisionRequestParameters.builder().modelName(builder.modelName).build();
        this.listeners = copy(builder.listeners);
    }

    @Override
    public DecisionResponse doDecide(DecisionRequest request) {
        DecisionCreateRequest openAiRequest = DecisionMapper.toOpenAiRequest(request);
        DecisionCreateResponse response =
                withRetryMappingExceptions(() -> client.decision(openAiRequest).execute(), maxRetries);
        return DecisionMapper.toDecisionResponse(response, request);
    }

    @Override
    public CompletableFuture<DecisionResponse> doDecideAsync(DecisionRequest request) {
        DecisionCreateRequest openAiRequest = DecisionMapper.toOpenAiRequest(request);
        CompletableFuture<ParsedAndRawResponse<DecisionCreateResponse>> responseFuture =
                withRetryMappingExceptionsAsync(() -> client.decision(openAiRequest).executeRawAsync(), maxRetries);
        CompletableFuture<DecisionResponse> result = responseFuture.thenApply(
                response -> DecisionMapper.toDecisionResponse(response.parsedResponse(), request));
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
        return ModelProvider.OPEN_AI;
    }

    public static OpenAiDecisionModelBuilder builder() {
        for (OpenAiDecisionModelBuilderFactory factory : loadFactories(OpenAiDecisionModelBuilderFactory.class)) {
            return factory.get();
        }
        return new OpenAiDecisionModelBuilder();
    }

    public static class OpenAiDecisionModelBuilder {

        private HttpClientBuilder httpClientBuilder;
        private String baseUrl;
        private String apiKey;
        private String organizationId;
        private String projectId;
        private String modelName;
        private Duration timeout;
        private Integer maxRetries;
        private Boolean logRequests;
        private Boolean logResponses;
        private Logger logger;
        private Supplier<Map<String, String>> customHeadersSupplier;
        private Map<String, String> customQueryParams;
        private List<DecisionModelListener> listeners;

        public OpenAiDecisionModelBuilder() {
            // This is public so it can be extended
        }

        /**
         * Sets a custom HTTP client builder, allowing fine-grained control over the HTTP client configuration such as
         * timeouts and proxy settings.
         */
        public OpenAiDecisionModelBuilder httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return this;
        }

        /**
         * The base URL of the API. Defaults to {@code https://api.openai.com/v1}.
         */
        public OpenAiDecisionModelBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public OpenAiDecisionModelBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public OpenAiDecisionModelBuilder organizationId(String organizationId) {
            this.organizationId = organizationId;
            return this;
        }

        public OpenAiDecisionModelBuilder projectId(String projectId) {
            this.projectId = projectId;
            return this;
        }

        /**
         * The model to use when a request does not specify one, for example {@code gpt-6-luna}. The model name must be
         * set either here or on each request.
         */
        public OpenAiDecisionModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        /**
         * The model to use when a request does not specify one. The model name must be set either here or on each
         * request.
         */
        public OpenAiDecisionModelBuilder modelName(OpenAiDecisionModelName modelName) {
            this.modelName = modelName.toString();
            return this;
        }

        /**
         * The connect and read timeout. Default values are 15 seconds (connect) and 60 seconds (read).
         */
        public OpenAiDecisionModelBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * The number of retries after the first attempt, for transient errors such as timeouts, rate limits and
         * server errors. Default value is 2.
         */
        public OpenAiDecisionModelBuilder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        /**
         * Whether to log requests. Default value is {@code false}.
         */
        public OpenAiDecisionModelBuilder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        /**
         * Whether to log responses. Default value is {@code false}.
         */
        public OpenAiDecisionModelBuilder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        /**
         * @param logger an alternate {@link Logger} to be used instead of the default one provided by LangChain4j for
         *               logging requests and responses.
         * @return {@code this}.
         */
        public OpenAiDecisionModelBuilder logger(Logger logger) {
            this.logger = logger;
            return this;
        }

        /**
         * Sets custom HTTP headers.
         */
        public OpenAiDecisionModelBuilder customHeaders(Map<String, String> customHeaders) {
            this.customHeadersSupplier = () -> customHeaders;
            return this;
        }

        /**
         * Sets a supplier for custom HTTP headers.
         * The supplier is called before each request, allowing dynamic header values.
         * For example, this is useful for OAuth2 tokens that expire and need refreshing.
         */
        public OpenAiDecisionModelBuilder customHeaders(Supplier<Map<String, String>> customHeadersSupplier) {
            this.customHeadersSupplier = customHeadersSupplier;
            return this;
        }

        public OpenAiDecisionModelBuilder customQueryParams(Map<String, String> customQueryParams) {
            this.customQueryParams = customQueryParams;
            return this;
        }

        public OpenAiDecisionModelBuilder listeners(List<DecisionModelListener> listeners) {
            this.listeners = listeners;
            return this;
        }

        public OpenAiDecisionModelBuilder listeners(DecisionModelListener... listeners) {
            return listeners(List.of(listeners));
        }

        public OpenAiDecisionModel build() {
            return new OpenAiDecisionModel(this);
        }
    }
}
