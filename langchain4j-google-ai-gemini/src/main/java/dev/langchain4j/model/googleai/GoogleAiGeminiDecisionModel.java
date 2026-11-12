package dev.langchain4j.model.googleai;

import static dev.langchain4j.internal.CompletableFutureUtils.propagateCancellation;
import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptionsAsync;
import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getOrDefault;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.message.AudioContent;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.PdfFileContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.VideoContent;
import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.exception.InvalidRequestException;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.listener.DecisionModelListener;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.response.DecisionResponse;
import dev.langchain4j.model.decision.response.RefusalAnswer;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * A {@link DecisionModel} backed by the <a href="https://aistudio.google.com/docs/decisions-api">Gemini Decisions
 * API</a> ({@code POST /v1beta/decisions} of the Gemini Developer API), which answers yes/no, choice and scale
 * questions about a text, structured, media or conversation input, with probabilities.
 * <pre>{@code
 * DecisionModel model = GoogleAiGeminiDecisionModel.builder()
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
 * Images, audio, video and PDF files can be given as base64 data, as {@code data:} URLs, or as URLs that Gemini
 * reads itself: publicly accessible {@code https} URLs, or the URIs of files uploaded with {@link GeminiFiles}. URLs
 * are passed to Gemini as they are, they are not downloaded by this class. The MIME type is taken from the content,
 * or guessed from the URL when the content has none. The detail level of an image is sent as its resolution;
 * an image without a detail level, or with {@code AUTO}, gets Gemini's default resolution.
 * <p>
 * Each question is sent under its name, and Gemini accepts only names made of the characters {@code A-Z},
 * {@code a-z}, {@code 0-9}, {@code _} and {@code -}, 1 to 64 characters long. Gemini checks this and the other limits
 * of the API, such as the number of questions, options and levels, and the size of the input, and rejects a request
 * that exceeds them with an {@link InvalidRequestException}.
 * <p>
 * Gemini can block the whole input, for example because it goes against its safety policies. Then no question is
 * answered: the answer to each question is a {@link RefusalAnswer}, see {@link DecisionResponse#isRefused(String)}.
 *
 * @since 1.24.0
 */
@Experimental
public class GoogleAiGeminiDecisionModel implements DecisionModel {

    private final GeminiService geminiService;
    private final int maxRetries;
    private final GoogleAiGeminiDecisionRequestParameters defaultRequestParameters;
    private final List<DecisionModelListener> listeners;

    public GoogleAiGeminiDecisionModel(GoogleAiGeminiDecisionModelBuilder builder) {
        this.geminiService = new GeminiService(
                builder.httpClientBuilder,
                builder.apiKey,
                builder.baseUrl,
                false,
                getOrDefault(builder.logRequests, false),
                getOrDefault(builder.logResponses, false),
                builder.logger,
                builder.timeout,
                builder.customHeadersSupplier);
        this.maxRetries = getOrDefault(builder.maxRetries, 2);
        this.defaultRequestParameters = GoogleAiGeminiDecisionRequestParameters.builder()
                .modelName(builder.modelName)
                .customParameters(builder.customParameters)
                .build();
        this.listeners = copy(builder.listeners);
    }

    public static GoogleAiGeminiDecisionModelBuilder builder() {
        return new GoogleAiGeminiDecisionModelBuilder();
    }

    @Override
    public DecisionResponse doDecide(DecisionRequest request) {
        GeminiDecisionRequest geminiRequest = GeminiDecisionMapper.toGeminiRequest(request);
        GeminiDecisionResponse response =
                withRetryMappingExceptions(() -> geminiService.decide(geminiRequest), maxRetries);
        return GeminiDecisionMapper.toDecisionResponse(response, request);
    }

    @Override
    public CompletableFuture<DecisionResponse> doDecideAsync(DecisionRequest request) {
        try {
            GeminiDecisionRequest geminiRequest = GeminiDecisionMapper.toGeminiRequest(request);
            CompletableFuture<GeminiDecisionResponse> responseFuture =
                    withRetryMappingExceptionsAsync(() -> geminiService.decideAsync(geminiRequest), maxRetries);
            CompletableFuture<DecisionResponse> result = responseFuture.thenApply(
                    response -> GeminiDecisionMapper.toDecisionResponse(response, request));
            propagateCancellation(result, responseFuture);
            return result;
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    public GoogleAiGeminiDecisionRequestParameters defaultRequestParameters() {
        return defaultRequestParameters;
    }

    @Override
    public List<DecisionModelListener> listeners() {
        return listeners;
    }

    @Override
    public ModelProvider provider() {
        return ModelProvider.GOOGLE_AI_GEMINI;
    }

    public static class GoogleAiGeminiDecisionModelBuilder {

        private @Nullable HttpClientBuilder httpClientBuilder;
        private @Nullable String apiKey;
        private @Nullable String baseUrl;
        private @Nullable String modelName;
        private @Nullable Duration timeout;
        private @Nullable Integer maxRetries;
        private @Nullable Boolean logRequests;
        private @Nullable Boolean logResponses;
        private @Nullable Logger logger;
        private @Nullable Supplier<Map<String, String>> customHeadersSupplier;
        private @Nullable Map<String, Object> customParameters;
        private @Nullable List<DecisionModelListener> listeners;

        public GoogleAiGeminiDecisionModelBuilder() {
            // This is public so it can be extended
        }

        /**
         * Sets the {@link HttpClientBuilder} used to create the HTTP client that communicates with Gemini. When not
         * set ({@code null}), the HTTP client found on the classpath is used.
         * <p>
         * NOTE: {@link #timeout(Duration)} overrides timeouts set on the {@link HttpClientBuilder}.
         */
        public GoogleAiGeminiDecisionModelBuilder httpClientBuilder(@Nullable HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return this;
        }

        /**
         * The Gemini API key, sent in the {@code x-goog-api-key} header. A missing or wrong key is rejected by
         * Gemini with an {@link AuthenticationException}.
         */
        public GoogleAiGeminiDecisionModelBuilder apiKey(@Nullable String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * The base URL of the Gemini API, including its version. When not set ({@code null}),
         * {@code https://generativelanguage.googleapis.com/v1beta} is used. Requests are sent to
         * {@code <baseUrl>/decisions}.
         */
        public GoogleAiGeminiDecisionModelBuilder baseUrl(@Nullable String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /**
         * The decision model to use when a request does not specify one, for example {@code gemini-decision-1}. The
         * model name must be set either here or on each request.
         */
        public GoogleAiGeminiDecisionModelBuilder modelName(@Nullable String modelName) {
            this.modelName = modelName;
            return this;
        }

        /**
         * The connect and read timeout. When not set ({@code null}), the timeouts of the
         * {@link #httpClientBuilder(HttpClientBuilder)} are used, or 15 seconds (connect) and 60 seconds (read).
         */
        public GoogleAiGeminiDecisionModelBuilder timeout(@Nullable Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * The number of retries after the first attempt, for transient errors such as timeouts, rate limits and
         * server errors. Default value is 2.
         */
        public GoogleAiGeminiDecisionModelBuilder maxRetries(@Nullable Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        /**
         * Whether to log requests. The API key is masked. Default value is {@code false}.
         */
        public GoogleAiGeminiDecisionModelBuilder logRequests(@Nullable Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        /**
         * Whether to log responses. Default value is {@code false}.
         */
        public GoogleAiGeminiDecisionModelBuilder logResponses(@Nullable Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        /**
         * An alternate {@link Logger} to be used instead of the default one for logging requests and responses.
         */
        public GoogleAiGeminiDecisionModelBuilder logger(@Nullable Logger logger) {
            this.logger = logger;
            return this;
        }

        /**
         * Sets custom HTTP headers.
         */
        public GoogleAiGeminiDecisionModelBuilder customHeaders(@Nullable Map<String, String> customHeaders) {
            this.customHeadersSupplier = () -> customHeaders;
            return this;
        }

        /**
         * Sets a supplier for custom HTTP headers. The supplier is called before each request, allowing dynamic
         * header values, for example OAuth2 tokens that expire and need refreshing.
         */
        public GoogleAiGeminiDecisionModelBuilder customHeaders(
                @Nullable Supplier<Map<String, String>> customHeadersSupplier) {
            this.customHeadersSupplier = customHeadersSupplier;
            return this;
        }

        /**
         * Custom parameters added to the body of every request, for example to use a feature of the Gemini Decisions
         * API that this class does not support yet. A custom parameter with the same name as a parameter set by this
         * class ({@code model}, {@code input} or {@code questions}) replaces it. They can be replaced for a single
         * request with {@link GoogleAiGeminiDecisionRequestParameters.Builder#customParameters(Map)}.
         */
        public GoogleAiGeminiDecisionModelBuilder customParameters(@Nullable Map<String, Object> customParameters) {
            this.customParameters = customParameters;
            return this;
        }

        /**
         * Sets the listeners that are notified of every request, response and error.
         */
        public GoogleAiGeminiDecisionModelBuilder listeners(@Nullable List<DecisionModelListener> listeners) {
            this.listeners = listeners;
            return this;
        }

        public GoogleAiGeminiDecisionModelBuilder listeners(DecisionModelListener... listeners) {
            return listeners(List.of(listeners));
        }

        public GoogleAiGeminiDecisionModel build() {
            return new GoogleAiGeminiDecisionModel(this);
        }
    }
}
