package dev.langchain4j.model.googleai;

import static dev.langchain4j.internal.CompletableFutureUtils.propagateCancellation;
import static dev.langchain4j.internal.Exceptions.unwrapCompletionException;
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
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.InvalidDecisionResponseException;
import dev.langchain4j.exception.InvalidRequestException;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.listener.DecisionModelListener;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.YesNoQuestion;
import dev.langchain4j.model.decision.response.DecisionResponse;
import dev.langchain4j.model.decision.response.RefusalAnswer;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
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
 * Gemini checks the limits of the API, such as the number of questions, options and levels, and the size of the
 * input, and rejects a request that exceeds them with an {@link InvalidRequestException}.
 * <p>
 * Gemini can block the whole input, for example because it goes against its safety policies. Then no question is
 * answered: the answer to each question is a {@link RefusalAnswer}, see {@link DecisionResponse#isRefused(String)}.
 * The reason is in the {@link GoogleAiGeminiDecisionResponseMetadata metadata} of the response, see
 * {@link GoogleAiGeminiDecisionResponseMetadata#blockReason()}.
 *
 * @since 1.24.0
 */
@Experimental
public class GoogleAiGeminiDecisionModel implements DecisionModel {

    private final GeminiService geminiService;
    private final int maxRetries;
    private final GoogleAiGeminiDecisionRequestParameters defaultRequestParameters;
    private final String yesWhenPrefix;
    private final String noWhenPrefix;
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
        this.yesWhenPrefix = getOrDefault(builder.yesWhenPrefix, "Also answer yes when: ");
        this.noWhenPrefix = getOrDefault(builder.noWhenPrefix, "Also answer no when: ");
        this.listeners = copy(builder.listeners);
    }

    public static GoogleAiGeminiDecisionModelBuilder builder() {
        return new GoogleAiGeminiDecisionModelBuilder();
    }

    @Override
    public DecisionResponse doDecide(DecisionRequest request) {
        GeminiDecisionRequest geminiRequest =
                GeminiDecisionMapper.toGeminiRequest(request, yesWhenPrefix, noWhenPrefix);
        GeminiDecisionResponse response = withRetryMappingExceptions(() -> decide(geminiRequest), maxRetries);
        return GeminiDecisionMapper.toDecisionResponse(response, request);
    }

    private GeminiDecisionResponse decide(GeminiDecisionRequest request) {
        try {
            return geminiService.decide(request);
        } catch (HttpException error) {
            GeminiDecisionResponse blockedResponse = GeminiDecisionMapper.toBlockedResponse(error);
            if (blockedResponse == null) {
                throw error;
            }
            return blockedResponse;
        }
    }

    @Override
    public CompletableFuture<DecisionResponse> doDecideAsync(DecisionRequest request) {
        try {
            GeminiDecisionRequest geminiRequest =
                    GeminiDecisionMapper.toGeminiRequest(request, yesWhenPrefix, noWhenPrefix);
            CompletableFuture<GeminiDecisionResponse> responseFuture =
                    withRetryMappingExceptionsAsync(() -> decideAsync(geminiRequest), maxRetries);
            CompletableFuture<DecisionResponse> result = responseFuture.thenApply(
                    response -> GeminiDecisionMapper.toDecisionResponse(response, request));
            propagateCancellation(result, responseFuture);
            return result;
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private CompletableFuture<GeminiDecisionResponse> decideAsync(GeminiDecisionRequest request) {
        CompletableFuture<GeminiDecisionResponse> responseFuture = geminiService.decideAsync(request);
        CompletableFuture<GeminiDecisionResponse> result = responseFuture.exceptionally(error -> {
            if (unwrapCompletionException(error) instanceof HttpException httpError) {
                GeminiDecisionResponse blockedResponse = GeminiDecisionMapper.toBlockedResponse(httpError);
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
        private @Nullable String yesWhenPrefix;
        private @Nullable String noWhenPrefix;
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
         * The Gemini API key, sent in the {@code x-goog-api-key} header. When not set ({@code null}), no
         * {@code x-goog-api-key} header is sent, for example when the request is authenticated with
         * {@link #customHeaders(Map)}. Gemini rejects a request without any credentials with an
         * {@link AuthenticationException} (HTTP 403), and a wrong key with an {@link InvalidRequestException}
         * (HTTP 400, "API key not valid").
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
         * An alternate {@link Logger} to be used instead of the default one for logging requests and responses. When
         * not set ({@code null}), the default logger is used.
         */
        public GoogleAiGeminiDecisionModelBuilder logger(@Nullable Logger logger) {
            this.logger = logger;
            return this;
        }

        /**
         * Sets custom HTTP headers, added to every request. When not set ({@code null}), no custom headers are added.
         */
        public GoogleAiGeminiDecisionModelBuilder customHeaders(@Nullable Map<String, String> customHeaders) {
            this.customHeadersSupplier = () -> customHeaders;
            return this;
        }

        /**
         * Sets a supplier for custom HTTP headers. The supplier is called before each request, allowing dynamic
         * header values, for example OAuth2 tokens that expire and need refreshing. When not set ({@code null}), or
         * when the supplier returns {@code null}, no custom headers are added.
         */
        public GoogleAiGeminiDecisionModelBuilder customHeaders(
                @Nullable Supplier<Map<String, String>> customHeadersSupplier) {
            this.customHeadersSupplier = customHeadersSupplier;
            return this;
        }

        /**
         * Custom parameters added to the body of every request, for example to use a feature of the Gemini Decisions
         * API that this class does not support yet. A custom parameter with the same name as a parameter set by this
         * class ({@code model}, {@code input} or {@code questions}) replaces it. When not set ({@code null}) or empty,
         * no custom parameters are added. They can be replaced for a single request with
         * {@link GoogleAiGeminiDecisionRequestParameters.Builder#customParameters(Map)}.
         * <p>
         * Do not replace {@code questions}: the answers are read against the questions of the
         * {@link DecisionRequest}, so the response would be rejected with an
         * {@link InvalidDecisionResponseException}.
         */
        public GoogleAiGeminiDecisionModelBuilder customParameters(@Nullable Map<String, Object> customParameters) {
            this.customParameters = customParameters;
            return this;
        }

        /**
         * The text put before {@link YesNoQuestion#yesWhen()} when a yes/no question has a {@code yesWhen} but no
         * {@code noWhen}. Such a question is sent to Gemini as its text, a line break, this prefix and the
         * {@code yesWhen}, for example:
         * <pre>
         * Is this a return request?
         * Also answer yes when: The customer wants to exchange an item for another size.
         * </pre>
         * When not set ({@code null}), {@code "Also answer yes when: "} is used.
         * <p>
         * A {@code yesWhen} on its own is one reason to answer yes, not the only one (see {@link YesNoQuestion}).
         * The default prefix says so: without "Also", Gemini tends to answer no whenever the {@code yesWhen} does not
         * hold. A question with both {@code yesWhen} and {@code noWhen} is sent with Gemini's own criteria fields,
         * and this prefix is not used.
         */
        public GoogleAiGeminiDecisionModelBuilder yesWhenPrefix(@Nullable String yesWhenPrefix) {
            this.yesWhenPrefix = yesWhenPrefix;
            return this;
        }

        /**
         * The text put before {@link YesNoQuestion#noWhen()} when a yes/no question has a {@code noWhen} but no
         * {@code yesWhen}. Such a question is sent to Gemini as its text, a line break, this prefix and the
         * {@code noWhen}. When not set ({@code null}), {@code "Also answer no when: "} is used.
         * <p>
         * A {@code noWhen} on its own is one reason to answer no, not the only one (see {@link YesNoQuestion}). A
         * question with both {@code yesWhen} and {@code noWhen} is sent with Gemini's own criteria fields, and this
         * prefix is not used.
         */
        public GoogleAiGeminiDecisionModelBuilder noWhenPrefix(@Nullable String noWhenPrefix) {
            this.noWhenPrefix = noWhenPrefix;
            return this;
        }

        /**
         * Sets the listeners that are notified of every request, response and error. When not set ({@code null}), no
         * listeners are notified.
         */
        public GoogleAiGeminiDecisionModelBuilder listeners(@Nullable List<DecisionModelListener> listeners) {
            this.listeners = listeners;
            return this;
        }

        /**
         * Sets the listeners that are notified of every request, response and error.
         */
        public GoogleAiGeminiDecisionModelBuilder listeners(DecisionModelListener... listeners) {
            return listeners(List.of(listeners));
        }

        public GoogleAiGeminiDecisionModel build() {
            return new GoogleAiGeminiDecisionModel(this);
        }
    }
}
