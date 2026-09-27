package dev.langchain4j.model.typesafe;

import static dev.langchain4j.internal.CompletableFutureUtils.propagateCancellation;
import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptionsAsync;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static java.time.Duration.ofSeconds;

import dev.langchain4j.Experimental;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.request.ChoiceQuestion;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.DecisionRequestParameters;
import dev.langchain4j.model.decision.request.NoulQuestion;
import dev.langchain4j.model.decision.request.Question;
import dev.langchain4j.model.decision.request.ScoreQuestion;
import dev.langchain4j.model.decision.response.ChoiceAnswer;
import dev.langchain4j.model.decision.response.DecisionAnswer;
import dev.langchain4j.model.decision.response.DecisionResponse;
import dev.langchain4j.model.decision.response.NoulAnswer;
import dev.langchain4j.model.decision.response.ScoreAnswer;
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
import org.slf4j.Logger;

/**
 * A {@link DecisionModel} backed by the <a href="https://docs.typesafe.ai">TypeSafe System One API</a>
 * ({@code POST /v1/systemone}), which serves decision models such as Jev.
 * <p>
 * Other servers that implement the same API (for example OpenRouter, or self-hosted open models) can be used by
 * setting {@link Builder#baseUrl(String)}.
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

    public TypeSafeDecisionModel(Builder builder) {
        this.client = TypeSafeClient.builder()
                .httpClientBuilder(builder.httpClientBuilder)
                .baseUrl(getOrDefault(builder.baseUrl, DEFAULT_BASE_URL))
                .apiKey(builder.apiKey)
                .timeout(getOrDefault(builder.timeout, ofSeconds(60)))
                .logRequests(getOrDefault(builder.logRequests, false))
                .logResponses(getOrDefault(builder.logResponses, false))
                .logger(builder.logger)
                .build();
        this.maxRetries = getOrDefault(builder.maxRetries, 2);
        this.defaultRequestParameters =
                DecisionRequestParameters.builder().modelName(builder.modelName).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public DecisionResponse doDecide(DecisionRequest request) {
        Map<String, Object> body = toRequestBody(request);
        TypeSafeResponse response = withRetryMappingExceptions(() -> client.decide(body), maxRetries);
        return toDecisionResponse(response, request);
    }

    @Override
    public CompletableFuture<DecisionResponse> doDecideAsync(DecisionRequest request) {
        Map<String, Object> body;
        try {
            body = toRequestBody(request);
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
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

    private Map<String, Object> toRequestBody(DecisionRequest request) {
        Map<String, Object> questions = new LinkedHashMap<>();
        request.questions().forEach((name, question) -> questions.put(name, toQuestion(question)));

        Map<String, Object> body = new LinkedHashMap<>();
        String modelName = getOrDefault(request.modelName(), defaultRequestParameters.modelName());
        body.put("model", ensureNotBlank(modelName, "modelName"));
        body.put("state", request.state());
        body.put("questions", questions);
        return body;
    }

    private static Map<String, Object> toQuestion(Question question) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (question instanceof NoulQuestion noul) {
            result.put("type", "noul");
            result.put("instructions", noul.instructions());
            Map<String, Object> criteria = new LinkedHashMap<>();
            if (noul.whenTrue() != null) {
                criteria.put("true", noul.whenTrue());
            }
            if (noul.whenFalse() != null) {
                criteria.put("false", noul.whenFalse());
            }
            if (!criteria.isEmpty()) {
                result.put("criteria", criteria);
            }
        } else if (question instanceof ChoiceQuestion choice) {
            result.put("type", "choice");
            result.put("instructions", choice.instructions());
            result.put("criteria", choice.options());
        } else if (question instanceof ScoreQuestion score) {
            result.put("type", "score");
            result.put("instructions", score.instructions());
            result.put("criteria", score.levels());
        } else {
            throw new UnsupportedFeatureException(
                    "TypeSafe does not support " + question.getClass().getName() + " questions");
        }
        return result;
    }

    private static DecisionResponse toDecisionResponse(TypeSafeResponse response, DecisionRequest request) {
        DecisionResponse.Builder builder = DecisionResponse.builder().modelName(response.model);
        if (response.usage != null) {
            builder.tokenUsage(new TokenUsage(response.usage.inputTokens, response.usage.outputTokens));
        }
        if (response.answers != null) {
            request.questions().forEach((name, question) -> {
                TypeSafeAnswer answer = response.answers.get(name);
                if (answer != null) {
                    builder.answer(name, toAnswer(answer, question));
                }
            });
        }
        return builder.build();
    }

    private static DecisionAnswer toAnswer(TypeSafeAnswer answer, Question question) {
        if (question instanceof NoulQuestion) {
            return NoulAnswer.builder().probability(clamp(answer.noul)).build();
        }
        if (question instanceof ChoiceQuestion) {
            Map<String, Double> probabilities = new LinkedHashMap<>();
            if (answer.probabilities != null) {
                answer.probabilities.forEach((option, probability) -> probabilities.put(option, clamp(probability)));
            }
            return ChoiceAnswer.builder()
                    .choice(answer.choice)
                    .probabilities(probabilities)
                    .confidence(clamp(answer.confidence))
                    .build();
        }
        ScoreQuestion score = (ScoreQuestion) question;
        List<Double> probabilities = new ArrayList<>();
        if (answer.probabilities != null && !answer.probabilities.isEmpty()) {
            for (int level = 0; level < score.levels().size(); level++) {
                probabilities.add(clamp(answer.probabilities.getOrDefault(String.valueOf(level), 0.0)));
            }
        }
        return ScoreAnswer.builder()
                .score(answer.score)
                .probabilities(probabilities)
                .confidence(clamp(answer.confidence))
                .build();
    }

    private static Double clamp(Double probability) {
        if (probability == null) {
            return null;
        }
        return Math.min(1.0, Math.max(0.0, probability)); // absorbs rounding errors such as 1.0000000002
    }

    public static class Builder {

        private HttpClientBuilder httpClientBuilder;
        private String baseUrl;
        private String apiKey;
        private String modelName;
        private Duration timeout;
        private Integer maxRetries;
        private Boolean logRequests;
        private Boolean logResponses;
        private Logger logger;

        /**
         * Sets a custom HTTP client builder, allowing fine-grained control over the HTTP client configuration such as
         * timeouts and proxy settings.
         */
        public Builder httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return this;
        }

        /**
         * The base URL of the API. Defaults to {@code https://api.typesafe.ai}. Set it to use another server that
         * implements the System One API.
         */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * The model to use when a request does not specify one, for example {@code jev-latest}. The model name must
         * be set either here or on each request.
         */
        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

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

        /**
         * An alternate {@link Logger} to be used instead of the default one for logging requests and responses.
         */
        public Builder logger(Logger logger) {
            this.logger = logger;
            return this;
        }

        public TypeSafeDecisionModel build() {
            return new TypeSafeDecisionModel(this);
        }
    }
}
