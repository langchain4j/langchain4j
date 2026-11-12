package dev.langchain4j.model.google.genai;

import static dev.langchain4j.internal.CompletableFutureUtils.propagateCancellation;
import static dev.langchain4j.internal.Exceptions.unwrapCompletionException;
import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.model.google.genai.GoogleGenAiDecisionMapper.toCreateDecisionRequest;
import static dev.langchain4j.model.google.genai.GoogleGenAiDecisionMapper.toDecisionResponse;

import com.google.genai.Client;
import com.google.genai.gaos.models.decisions.CreateDecisionRequest;
import com.google.genai.gaos.models.decisions.CreateDecisionResponse;
import dev.langchain4j.Experimental;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.exception.InvalidDecisionResponseException;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link DecisionModel} backed by the <a href="https://ai.google.dev/api/decisions">Gemini Decisions API</a>, such
 * as {@code gemini-decision-1}, using the official com.google.genai SDK. It answers yes/no, choice and scale questions
 * about a text, image, PDF, audio, video or conversation input with calibrated probabilities.
 * <pre>{@code
 * DecisionModel model = GoogleGenAiDecisionModel.builder()
 *         .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
 *         .modelName("gemini-decision-1")
 *         .build();
 * }</pre>
 * The input can be text, named fields (each sent as a text part labeled with its name, and media after their label),
 * a list of {@link Content}s, or a conversation (a list of {@link ChatMessage}s, sent as a JSON transcript followed
 * by its images), see {@link DecisionRequest}. Images, PDFs, audio and video can be base64 data, the URI of a file
 * uploaded with the Gemini Files API, or a publicly accessible {@code https} URL, which Gemini downloads itself. The
 * {@link ImageContent.DetailLevel} of an image is sent as its resolution, {@code AUTO} leaves the resolution to the
 * model.
 * <p>
 * If Gemini blocks the input, for example for safety reasons, every question is answered with a
 * {@link RefusalAnswer}.
 * <p>
 * The Decisions API is available on the Gemini API ({@code v1beta}), not on Vertex AI.
 *
 * @since 1.24.0
 */
@Experimental
public class GoogleGenAiDecisionModel implements DecisionModel {

    private static final Logger log = LoggerFactory.getLogger(GoogleGenAiDecisionModel.class);

    private final Client client;
    private final boolean logRequests;
    private final boolean logResponses;
    private final DecisionRequestParameters defaultRequestParameters;
    private final List<DecisionModelListener> listeners;

    private GoogleGenAiDecisionModel(Builder builder) {
        this.client = builder.client != null
                ? builder.client
                : GoogleGenAiClientFactory.createClient(
                        builder.apiKey,
                        null,
                        null,
                        null,
                        builder.timeout,
                        builder.customHeaders,
                        builder.apiEndpoint,
                        getOrDefault(builder.maxRetries, 3));
        this.logRequests = getOrDefault(builder.logRequests, false);
        this.logResponses = getOrDefault(builder.logResponses, false);
        this.defaultRequestParameters =
                DecisionRequestParameters.builder().modelName(builder.modelName).build();
        this.listeners = copy(builder.listeners);
    }

    @Override
    public DecisionResponse doDecide(DecisionRequest request) {
        CreateDecisionRequest createDecisionRequest = toCreateDecisionRequest(request);
        logRequest(createDecisionRequest);
        Optional<CreateDecisionResponse> response = GoogleGenAiExceptionMapper.INSTANCE.withExceptionMapper(
                () -> client.decisions.create(createDecisionRequest).createDecisionResponse());
        return toResponse(response, request);
    }

    @Override
    public CompletableFuture<DecisionResponse> doDecideAsync(DecisionRequest request) {
        CreateDecisionRequest createDecisionRequest = toCreateDecisionRequest(request);
        logRequest(createDecisionRequest);
        var decision = client.decisions.async().create(createDecisionRequest);
        CompletableFuture<DecisionResponse> result = decision.handle((response, error) -> {
            if (error != null) {
                throw GoogleGenAiExceptionMapper.INSTANCE.mapException(unwrapCompletionException(error));
            }
            return toResponse(response.createDecisionResponse(), request);
        });
        propagateCancellation(result, decision);
        return result;
    }

    private DecisionResponse toResponse(Optional<CreateDecisionResponse> response, DecisionRequest request) {
        if (logResponses) {
            log.info("Response:\n- response: {}", response.orElse(null));
        }
        return toDecisionResponse(response.orElseThrow(GoogleGenAiDecisionModel::emptyResponse), request);
    }

    private void logRequest(CreateDecisionRequest request) {
        if (logRequests) {
            log.info("Request:\n- request: {}", request);
        }
    }

    private static InvalidDecisionResponseException emptyResponse() {
        return new InvalidDecisionResponseException("The Gemini Decisions API returned no decision");
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
        return ModelProvider.GOOGLE_GENAI;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private Client client;
        private String apiKey;
        private Duration timeout;
        private String modelName;
        private Integer maxRetries;
        private Boolean logRequests;
        private Boolean logResponses;
        private String apiEndpoint;
        private Map<String, String> customHeaders;
        private List<DecisionModelListener> listeners;

        /**
         * Sets a preconfigured client. The other client settings (API key, timeout, endpoint and headers) are then
         * ignored. The Decisions API is served under {@code v1beta}, so the client must be built with
         * {@code HttpOptions.builder().apiVersion("v1beta")}, otherwise the API answers with HTTP 404.
         */
        public Builder client(Client client) {
            this.client = client;
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
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

        /**
         * The number of retries of the Google GenAI SDK for transient errors such as rate limits and server errors,
         * with exponential backoff. Default value is 3. Ignored when a {@link #client(Client)} is set: the retries of
         * that client apply.
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

        public Builder apiEndpoint(String apiEndpoint) {
            this.apiEndpoint = apiEndpoint;
            return this;
        }

        public Builder customHeaders(Map<String, String> customHeaders) {
            this.customHeaders = customHeaders;
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
