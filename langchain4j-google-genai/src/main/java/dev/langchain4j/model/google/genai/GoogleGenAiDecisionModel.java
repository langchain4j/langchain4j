package dev.langchain4j.model.google.genai;

import static dev.langchain4j.internal.CompletableFutureUtils.propagateCancellation;
import static dev.langchain4j.internal.Exceptions.unwrapCompletionException;
import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptionsAsync;
import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.model.google.genai.GoogleGenAiDecisionMapper.toBlockedResponse;
import static dev.langchain4j.model.google.genai.GoogleGenAiDecisionMapper.toCreateDecisionRequest;
import static dev.langchain4j.model.google.genai.GoogleGenAiDecisionMapper.toDecisionResponse;

import com.google.genai.Client;
import com.google.genai.gaos.models.decisions.CreateDecisionRequest;
import com.google.genai.gaos.models.decisions.CreateDecisionResponse;
import com.google.genai.gaos.models.errors.GaosApiException;
import com.google.genai.types.HttpRetryOptions;
import dev.langchain4j.Experimental;
import dev.langchain4j.data.message.AudioContent;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.PdfFileContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.VideoContent;
import dev.langchain4j.exception.InvalidDecisionResponseException;
import dev.langchain4j.exception.InvalidRequestException;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.listener.DecisionModelListener;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.response.DecisionResponse;
import dev.langchain4j.model.decision.response.RefusalAnswer;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link DecisionModel} backed by the <a href="https://aistudio.google.com/docs/decisions-api">Gemini Decisions
 * API</a> (such as {@code gemini-decision-1}), using the official Google GenAI Java SDK. It answers yes/no, choice and
 * scale questions about a shared input in a single call, with calibrated probabilities. The Gemini Decisions API is
 * available on the Gemini Developer API only, not on Vertex AI.
 * <pre>{@code
 * DecisionModel model = GoogleGenAiDecisionModel.builder()
 *         .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
 *         .modelName("gemini-decision-1")
 *         .build();
 * }</pre>
 * The input can be:
 * <ul>
 *     <li>text,</li>
 *     <li>named fields: each field is sent as text labeled with its name, or, for a field that holds contents such
 *     as an image, as its label followed by the contents,</li>
 *     <li>a list of {@link TextContent}s, {@link ImageContent}s, {@link AudioContent}s, {@link VideoContent}s and
 *     {@link PdfFileContent}s,</li>
 *     <li>a conversation (a list of {@link ChatMessage}s), sent as a JSON transcript followed by the images, audio,
 *     video and PDF files it refers to, for example as {@code [image 1]} or {@code [audio 1]}.</li>
 * </ul>
 * <p>
 * Gemini checks the limits of the API, such as the number of questions, options and levels, and the size of the input,
 * and rejects a request that exceeds them with an {@link InvalidRequestException}.
 * <p>
 * Gemini can block the whole input, for example because it goes against its safety policies. Then no question is
 * answered: the answer to each question is a {@link RefusalAnswer}, see {@link DecisionResponse#isRefused(String)}.
 * The reason is in the {@link GoogleGenAiDecisionResponseMetadata metadata} of the response, see
 * {@link GoogleGenAiDecisionResponseMetadata#blockReason()}.
 *
 * @since 1.24.0
 */
@Experimental
public class GoogleGenAiDecisionModel implements DecisionModel {

    private static final Logger log = LoggerFactory.getLogger(GoogleGenAiDecisionModel.class);
    private static final String GEMINI_API_DECISIONS_VERSION = "v1beta";
    // with attempts(1) alone, the SDK still sleeps up to 2 s after a 408, 429 or 5xx response
    private static final HttpRetryOptions NO_SDK_RETRIES =
            HttpRetryOptions.builder().attempts(1).httpStatusCodes(List.of()).build();

    private final Client client;
    private final @Nullable String apiVersion;
    private final GoogleGenAiDecisionRequestParameters defaultRequestParameters;
    private final int maxRetries;
    private final boolean logRequests;
    private final boolean logResponses;
    private final List<DecisionModelListener> listeners;

    public GoogleGenAiDecisionModel(Builder builder) {
        this.client = builder.client != null
                ? builder.client
                : GoogleGenAiClientFactory.createClient(
                        builder.apiKey,
                        null,
                        null,
                        null,
                        getOrDefault(builder.timeout, GoogleGenAiClientFactory.DEFAULT_TIMEOUT),
                        builder.customHeaders,
                        builder.apiEndpoint,
                        null,
                        NO_SDK_RETRIES);
        this.apiVersion =
                getOrDefault(builder.apiVersion, this.client.vertexAI() ? null : GEMINI_API_DECISIONS_VERSION);

        this.defaultRequestParameters = GoogleGenAiDecisionRequestParameters.builder()
                .modelName(builder.modelName)
                .customParameters(builder.customParameters)
                .build();

        this.maxRetries = getOrDefault(builder.maxRetries, 2);
        this.logRequests = getOrDefault(builder.logRequests, false);
        this.logResponses = getOrDefault(builder.logResponses, false);
        this.listeners = copy(builder.listeners);
    }

    @Override
    public DecisionResponse doDecide(DecisionRequest request) {
        CreateDecisionRequest createRequest = toCreateDecisionRequest(request);
        if (logRequests) {
            log.info("Decision request: {}", createRequest);
        }

        CreateDecisionResponse sdkResponse = withRetryMappingExceptions(
                () -> decide(createRequest), maxRetries, GoogleGenAiExceptionMapper.DECISION_INSTANCE);

        if (logResponses) {
            log.info("Decision response: {}", sdkResponse);
        }
        return toDecisionResponse(sdkResponse, request);
    }

    private CreateDecisionResponse decide(CreateDecisionRequest createRequest) {
        try {
            return unwrapResponse(
                    client.decisions.create(apiVersion, createRequest, null).createDecisionResponse());
        } catch (GaosApiException error) {
            CreateDecisionResponse blockedResponse = toBlockedResponse(error);
            if (blockedResponse != null) {
                return blockedResponse;
            }
            throw error;
        }
    }

    @Override
    public CompletableFuture<DecisionResponse> doDecideAsync(DecisionRequest request) {
        try {
            CreateDecisionRequest createRequest = toCreateDecisionRequest(request);
            if (logRequests) {
                log.info("Decision request: {}", createRequest);
            }

            CompletableFuture<CreateDecisionResponse> sdkFuture = withRetryMappingExceptionsAsync(
                    () -> decideAsync(createRequest), maxRetries, GoogleGenAiExceptionMapper.DECISION_INSTANCE);

            CompletableFuture<DecisionResponse> result = sdkFuture.thenApply(sdkResponse -> {
                if (logResponses) {
                    log.info("Decision response: {}", sdkResponse);
                }
                return toDecisionResponse(sdkResponse, request);
            });
            propagateCancellation(result, sdkFuture);
            return result;
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(GoogleGenAiExceptionMapper.DECISION_INSTANCE.mapException(e));
        }
    }

    private CompletableFuture<CreateDecisionResponse> decideAsync(CreateDecisionRequest createRequest) {
        var sdkFuture = client.async.decisions.create(apiVersion, createRequest, null);
        CompletableFuture<CreateDecisionResponse> responseFuture =
                sdkFuture.thenApply(op -> unwrapResponse(op.createDecisionResponse()));
        CompletableFuture<CreateDecisionResponse> result = responseFuture.exceptionally(error -> {
            if (unwrapCompletionException(error) instanceof GaosApiException gaosError) {
                CreateDecisionResponse blockedResponse = toBlockedResponse(gaosError);
                if (blockedResponse != null) {
                    return blockedResponse;
                }
            }
            throw error instanceof CompletionException completionException
                    ? completionException
                    : new CompletionException(error);
        });
        propagateCancellation(result, sdkFuture);
        return result;
    }

    private static CreateDecisionResponse unwrapResponse(Optional<CreateDecisionResponse> response) {
        return response.orElseThrow(
                () -> new InvalidDecisionResponseException("The Gemini Decisions API returned an empty response body"));
    }

    @Override
    public GoogleGenAiDecisionRequestParameters defaultRequestParameters() {
        return defaultRequestParameters;
    }

    @Override
    public List<DecisionModelListener> listeners() {
        return listeners;
    }

    @Override
    public ModelProvider provider() {
        return ModelProvider.GOOGLE_GENAI;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private @Nullable Client client;
        private @Nullable String apiKey;
        private @Nullable String apiEndpoint;
        private @Nullable String apiVersion;
        private @Nullable Duration timeout;
        private @Nullable Map<String, String> customHeaders;
        private @Nullable String modelName;
        private @Nullable Map<String, Object> customParameters;
        private @Nullable Integer maxRetries;
        private @Nullable Boolean logRequests;
        private @Nullable Boolean logResponses;
        private @Nullable List<DecisionModelListener> listeners;

        /**
         * Sets a preconfigured Google Gen AI {@link Client}. The other client settings of this builder
         * ({@code apiKey}, {@code timeout}, {@code customHeaders} and {@code apiEndpoint}) are then ignored. The
         * SDK client's own {@link HttpRetryOptions} run in addition to {@link #maxRetries(Integer)}: set
         * {@code HttpRetryOptions.builder().attempts(1).httpStatusCodes(List.of()).build()} on the client (or
         * {@code maxRetries(0)} here) to avoid stacking retries. When not set ({@code null}), a client is created
         * from the settings of this builder.
         */
        public Builder client(@Nullable Client client) {
            this.client = client;
            return this;
        }

        /**
         * The Gemini API key. When not set ({@code null}), the Google Gen AI SDK reads it from the
         * {@code GOOGLE_API_KEY} or {@code GEMINI_API_KEY} environment variable.
         */
        public Builder apiKey(@Nullable String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * Overrides the base URL of the API, for example to go through a proxy. When not set ({@code null}), the
         * SDK's default endpoint is used.
         */
        public Builder apiEndpoint(@Nullable String apiEndpoint) {
            this.apiEndpoint = apiEndpoint;
            return this;
        }

        /**
         * The version of the Gemini Developer API to call, for example when the Decisions API moves to a newer
         * version. It replaces the API version of a {@link #client(Client)}. When not set ({@code null}),
         * {@code v1beta} is used.
         */
        public Builder apiVersion(@Nullable String apiVersion) {
            this.apiVersion = apiVersion;
            return this;
        }

        /**
         * The maximum duration of one call to the API: connecting, sending the input, waiting for Gemini and
         * receiving the answer. Each retry gets the full timeout. When not set ({@code null}), 20 minutes.
         */
        public Builder timeout(@Nullable Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * HTTP headers added to every request. A {@code User-Agent} header is added to the SDK's own User-Agent.
         * When not set ({@code null}), no custom headers are added.
         */
        public Builder customHeaders(@Nullable Map<String, String> customHeaders) {
            this.customHeaders = customHeaders;
            return this;
        }

        /**
         * The model to use when a request does not specify one, for example {@code gemini-decision-1}. The model name
         * must be set either here or on each request.
         */
        public Builder modelName(@Nullable String modelName) {
            this.modelName = modelName;
            return this;
        }

        /**
         * Custom parameters added to the body of every request, for example to use a feature of the Gemini Decisions
         * API that this class does not support yet. {@code model}, {@code input} and {@code questions} are set by
         * this class and cannot be custom parameters: the request is rejected with an
         * {@link IllegalArgumentException} before anything is sent. They can be replaced for a single request with
         * {@link GoogleGenAiDecisionRequestParameters.Builder#customParameters(Map)}. When not set ({@code null}),
         * no custom parameters are added.
         */
        public Builder customParameters(@Nullable Map<String, Object> customParameters) {
            this.customParameters = customParameters;
            return this;
        }

        /**
         * The maximum number of retries for transient errors such as timeouts, rate limits and server errors.
         * Default value is 2.
         */
        public Builder maxRetries(@Nullable Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        /**
         * Whether to log each request at INFO level. Default value is {@code false}.
         */
        public Builder logRequests(@Nullable Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        /**
         * Whether to log each response at INFO level. Default value is {@code false}.
         */
        public Builder logResponses(@Nullable Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        /**
         * Sets both {@link #logRequests(Boolean)} and {@link #logResponses(Boolean)}.
         */
        public Builder logRequestsAndResponses(@Nullable Boolean logRequestsAndResponses) {
            this.logRequests = logRequestsAndResponses;
            this.logResponses = logRequestsAndResponses;
            return this;
        }

        /**
         * The listeners notified of every request, response and error, for example for observability. When not set
         * ({@code null}), there are none.
         */
        public Builder listeners(@Nullable List<DecisionModelListener> listeners) {
            this.listeners = listeners;
            return this;
        }

        public Builder listeners(DecisionModelListener... listeners) {
            return listeners(List.of(listeners));
        }

        public GoogleGenAiDecisionModel build() {
            return new GoogleGenAiDecisionModel(this);
        }
    }
}
