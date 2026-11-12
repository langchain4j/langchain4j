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

import com.google.auth.oauth2.GoogleCredentials;
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
import dev.langchain4j.model.decision.request.DecisionRequestParameters;
import dev.langchain4j.model.decision.response.DecisionResponse;
import dev.langchain4j.model.decision.response.RefusalAnswer;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link DecisionModel} backed by the <a href="https://aistudio.google.com/docs/decisions-api">Gemini Decisions
 * API</a> (such as {@code gemini-decision-1}), using the official Google GenAI Java SDK. It answers yes/no, choice and
 * scale questions about a shared input in a single call, with calibrated probabilities.
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

    private final Client client;
    private final String apiVersion;
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
                        builder.googleCredentials,
                        builder.projectId,
                        builder.location,
                        builder.timeout,
                        builder.customHeaders,
                        builder.apiEndpoint,
                        HttpRetryOptions.builder().attempts(1).build());
        this.apiVersion =
                getOrDefault(builder.apiVersion, this.client.vertexAI() ? null : GEMINI_API_DECISIONS_VERSION);

        DecisionRequestParameters commonParameters =
                getOrDefault(builder.defaultRequestParameters, () -> DecisionRequestParameters.EMPTY);
        this.defaultRequestParameters = GoogleGenAiDecisionRequestParameters.builder()
                .overrideWith(commonParameters)
                .modelName(getOrDefault(builder.modelName, commonParameters.modelName()))
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
        CompletableFuture<CreateDecisionResponse> responseFuture = client.async
                .decisions
                .create(apiVersion, createRequest, null)
                .thenApply(op -> unwrapResponse(op.createDecisionResponse()));
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
        propagateCancellation(result, responseFuture);
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

        private Client client;
        private String apiKey;
        private GoogleCredentials googleCredentials;
        private String projectId;
        private String location;
        private String apiEndpoint;
        private String apiVersion;
        private Duration timeout;
        private Map<String, String> customHeaders;
        private String modelName;
        private DecisionRequestParameters defaultRequestParameters;
        private Integer maxRetries;
        private Boolean logRequests;
        private Boolean logResponses;
        private List<DecisionModelListener> listeners;

        /**
         * Sets a preconfigured Google Gen AI {@link Client}. Other client settings ({@code apiKey},
         * {@code googleCredentials}, {@code projectId}, {@code location}, {@code timeout}, {@code customHeaders},
         * {@code apiEndpoint}) are then ignored. Note that the SDK client's own {@link HttpRetryOptions} run in
         * addition to {@link #maxRetries(Integer)}: set {@code HttpRetryOptions.builder().attempts(1).build()} on the
         * client (or {@code maxRetries(0)} here) to avoid stacking retries.
         */
        public Builder client(Client client) {
            this.client = client;
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder googleCredentials(GoogleCredentials googleCredentials) {
            this.googleCredentials = googleCredentials;
            return this;
        }

        public Builder projectId(String projectId) {
            this.projectId = projectId;
            return this;
        }

        public Builder location(String location) {
            this.location = location;
            return this;
        }

        public Builder apiEndpoint(String apiEndpoint) {
            this.apiEndpoint = apiEndpoint;
            return this;
        }

        /**
         * Overrides the API version segment used when routing requests (defaults to {@code v1beta} on the Gemini
         * Developer API, and to the SDK's Vertex AI routing when using Vertex AI).
         */
        public Builder apiVersion(String apiVersion) {
            this.apiVersion = apiVersion;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder customHeaders(Map<String, String> customHeaders) {
            this.customHeaders = customHeaders;
            return this;
        }

        /**
         * The model to use when a request does not specify one, for example {@code gemini-decision-1}. The model name
         * must be set either here or on each request.
         */
        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public Builder defaultRequestParameters(DecisionRequestParameters defaultRequestParameters) {
            this.defaultRequestParameters = defaultRequestParameters;
            return this;
        }

        /**
         * The maximum number of retries for transient errors such as timeouts, rate limits and server errors.
         * Default value is 2.
         */
        public Builder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
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

        public Builder logRequestsAndResponses(Boolean logRequestsAndResponses) {
            this.logRequests = logRequestsAndResponses;
            this.logResponses = logRequestsAndResponses;
            return this;
        }

        public Builder listeners(List<DecisionModelListener> listeners) {
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
