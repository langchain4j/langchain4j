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
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.exception.InvalidDecisionResponseException;
import dev.langchain4j.model.decision.listener.DecisionModelListener;
import dev.langchain4j.model.decision.request.ChoiceQuestion;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.DecisionRequestParameters;
import dev.langchain4j.model.decision.request.YesNoQuestion;
import dev.langchain4j.model.decision.request.Question;
import dev.langchain4j.model.decision.request.ScaleQuestion;
import dev.langchain4j.model.decision.response.ChoiceAnswer;
import dev.langchain4j.model.decision.response.DecisionAnswer;
import dev.langchain4j.model.decision.response.DecisionResponse;
import dev.langchain4j.model.decision.response.YesNoAnswer;
import dev.langchain4j.model.decision.response.ScaleAnswer;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.model.typesafe.internal.TypeSafeAnswer;
import dev.langchain4j.model.typesafe.internal.TypeSafeClient;
import dev.langchain4j.model.typesafe.internal.TypeSafeResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
    private static final double PROBABILITY_TOLERANCE = 1e-6;

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
        Map<String, Object> body = toRequestBody(request);
        TypeSafeResponse response = withRetryMappingExceptions(() -> client.decide(body), maxRetries);
        return toDecisionResponse(response, request);
    }

    @Override
    public CompletableFuture<DecisionResponse> doDecideAsync(DecisionRequest request) {
        Map<String, Object> body = toRequestBody(request);
        CompletableFuture<TypeSafeResponse> responseFuture =
                withRetryMappingExceptionsAsync(() -> client.decideAsync(body), maxRetries);
        CompletableFuture<DecisionResponse> result =
                responseFuture.thenApply(response -> toDecisionResponse(response, request));
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

    private Map<String, Object> toRequestBody(DecisionRequest request) {
        Map<String, Object> questions = new LinkedHashMap<>();
        request.questions().forEach((name, question) -> questions.put(name, toQuestion(question)));

        Map<String, Object> body = new LinkedHashMap<>();
        if (isNullOrBlank(request.modelName())) {
            throw new IllegalArgumentException("The model name must be set, either with "
                    + "TypeSafeDecisionModel.builder().modelName(...) or on the request with "
                    + "DecisionRequestParameters.builder().modelName(...)");
        }
        body.put("model", request.modelName());
        body.put("state", request.input());
        body.put("questions", questions);
        return body;
    }

    private static Map<String, Object> toQuestion(Question question) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (question instanceof YesNoQuestion yesNo) {
            result.put("type", "noul");
            result.put("instructions", yesNo.text());
            Map<String, Object> criteria = new LinkedHashMap<>();
            if (yesNo.yesWhen() != null) {
                criteria.put("true", yesNo.yesWhen());
            }
            if (yesNo.noWhen() != null) {
                criteria.put("false", yesNo.noWhen());
            }
            if (!criteria.isEmpty()) {
                result.put("criteria", criteria);
            }
        } else if (question instanceof ChoiceQuestion choice) {
            result.put("type", "choice");
            result.put("instructions", choice.text());
            result.put("criteria", choice.options());
        } else if (question instanceof ScaleQuestion scale) {
            result.put("type", "score");
            result.put("instructions", scale.text());
            result.put("criteria", scale.levels());
        } else {
            throw new UnsupportedFeatureException(
                    "TypeSafe does not support " + question.getClass().getName() + " questions");
        }
        return result;
    }

    private static DecisionResponse toDecisionResponse(TypeSafeResponse response, DecisionRequest request) {
        Map<String, TypeSafeAnswer> answers = getOrDefault(response.answers, Map.of());
        DecisionResponse.Builder builder = DecisionResponse.builder().modelName(response.model);
        if (response.usage != null) {
            builder.tokenUsage(new TokenUsage(response.usage.inputTokens, response.usage.outputTokens));
        }
        request.questions().forEach((name, question) -> {
            TypeSafeAnswer answer = answers.get(name);
            if (answer == null) {
                throw new InvalidDecisionResponseException("The response contains no answer to question '%s'"
                        .formatted(name));
            }
            builder.answer(name, toAnswer(name, answer, question));
        });
        return builder.build();
    }

    private static DecisionAnswer toAnswer(String name, TypeSafeAnswer answer, Question question) {
        if (question instanceof YesNoQuestion) {
            ensureType(name, answer, "noul");
            return YesNoAnswer.builder()
                    .probability(probability(name, "noul", required(name, "noul", answer.noul)))
                    .build();
        }
        if (question instanceof ChoiceQuestion choice) {
            ensureType(name, answer, "choice");
            String value = required(name, "choice", answer.choice);
            if (!choice.options().containsKey(value)) {
                throw invalid(name, "chose '%s', which is not one of the options %s", value, choice.options().keySet());
            }
            Map<String, Double> probabilities = new LinkedHashMap<>();
            if (answer.probabilities != null) {
                answer.probabilities.forEach((option, probability) -> {
                    if (!choice.options().containsKey(option)) {
                        throw invalid(name, "has a probability for '%s', which is not one of the options %s",
                                option, choice.options().keySet());
                    }
                    probabilities.put(option, probability(name, "probability of '" + option + "'", probability));
                });
            }
            return ChoiceAnswer.builder()
                    .value(value)
                    .probabilities(probabilities)
                    .confidence(answer.confidence == null ? null : probability(name, "confidence", answer.confidence))
                    .build();
        }
        ScaleQuestion scale = (ScaleQuestion) question;
        ensureType(name, answer, "score");
        int levels = scale.levels().size();
        List<Double> probabilities = new ArrayList<>();
        if (answer.probabilities != null && !answer.probabilities.isEmpty()) {
            answer.probabilities.keySet().forEach(level -> {
                if (!isLevelIndex(level, levels)) {
                    throw invalid(name, "has a probability for level '%s', but the levels are 0 to %s", level, levels - 1);
                }
            });
            for (int level = 0; level < levels; level++) {
                Double probability = answer.probabilities.getOrDefault(String.valueOf(level), 0.0);
                probabilities.add(probability(name, "probability of level " + level, probability));
            }
        }
        return ScaleAnswer.builder()
                .mean(level(name, required(name, "score", answer.score), levels))
                .probabilities(probabilities)
                .confidence(answer.confidence == null ? null : probability(name, "confidence", answer.confidence))
                .build();
    }

    private static void ensureType(String name, TypeSafeAnswer answer, String expectedType) {
        if (answer.type != null && !answer.type.equals(expectedType)) {
            throw invalid(name, "has type '%s' instead of '%s'", answer.type, expectedType);
        }
    }

    private static <T> T required(String name, String field, T value) {
        if (value == null) {
            throw invalid(name, "has no '%s'", field);
        }
        return value;
    }

    private static double probability(String name, String field, Double value) {
        if (value == null || value.isNaN() || value < -PROBABILITY_TOLERANCE || value > 1 + PROBABILITY_TOLERANCE) {
            throw invalid(name, "has an invalid %s: %s", field, value);
        }
        return Math.min(1.0, Math.max(0.0, value)); // absorbs rounding errors such as 1.0000000002
    }

    private static double level(String name, double value, int levels) {
        if (Double.isNaN(value) || value < -PROBABILITY_TOLERANCE || value > levels - 1 + PROBABILITY_TOLERANCE) {
            throw invalid(name, "has an invalid score: %s, but the levels are 0 to %s", value, levels - 1);
        }
        return Math.min(levels - 1, Math.max(0.0, value));
    }

    private static boolean isLevelIndex(String level, int levels) {
        try {
            int index = Integer.parseInt(level);
            return index >= 0 && index < levels && String.valueOf(index).equals(level);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static InvalidDecisionResponseException invalid(String name, String format, Object... args) {
        return new InvalidDecisionResponseException(
                "The answer to question '%s' %s".formatted(name, format.formatted(args)));
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
