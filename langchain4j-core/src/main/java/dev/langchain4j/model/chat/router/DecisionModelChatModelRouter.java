package dev.langchain4j.model.chat.router;

import static dev.langchain4j.internal.CompletableFutureUtils.propagateCancellation;
import static dev.langchain4j.internal.Exceptions.unwrapCompletionException;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureBetween;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.util.stream.Collectors.joining;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.request.ChoiceQuestion;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.response.ChoiceAnswer;
import dev.langchain4j.model.decision.response.DecisionResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link ChatModelRouter} that uses a {@link DecisionModel} to select the route: the decision model chooses the
 * route whose description fits the last user message best.
 * <pre>{@code
 * ChatModel chatModel = RoutingChatModel.builder()
 *         .route("simple", smallModel, "Greetings, short factual questions, simple lookups")
 *         .route("complex", largeModel, "Multi-step reasoning, code, analysis")
 *         .router(new DecisionModelChatModelRouter(decisionModel))
 *         .defaultRoute("complex")
 *         .build();
 * }</pre>
 * Every route needs a description. The router returns {@code null} (so the default route of the routing chat model
 * is used) when the request contains no user message, when the probability of the chosen route is below
 * {@link Builder#minProbability(Double)}, or when the decision model fails. When only one route can handle the
 * request (for example, the only route supporting a JSON schema response format), that route is selected without
 * calling the decision model.
 *
 * @since 1.21.0
 */
@Experimental
public class DecisionModelChatModelRouter implements ChatModelRouter {

    private static final Logger log = LoggerFactory.getLogger(DecisionModelChatModelRouter.class);

    private static final String DEFAULT_QUESTION = "Which model should handle this request?";
    private static final String QUESTION_NAME = "route";

    private final DecisionModel decisionModel;
    private final String question;
    private final Double minProbability;

    public DecisionModelChatModelRouter(DecisionModel decisionModel) {
        this(builder().decisionModel(decisionModel));
    }

    protected DecisionModelChatModelRouter(Builder builder) {
        this.decisionModel = ensureNotNull(builder.decisionModel, "decisionModel");
        this.question = ensureNotBlank(getOrDefault(builder.question, DEFAULT_QUESTION), "question");
        this.minProbability = builder.minProbability == null
                ? null
                : ensureBetween(builder.minProbability, 0, 1, "minProbability");
    }

    @Override
    public String route(ChatModelRoutingRequest request) {
        if (request.routes().size() == 1) {
            return request.routes().get(0).name();
        }
        DecisionRequest decisionRequest = toDecisionRequest(request);
        if (decisionRequest == null) {
            return null;
        }
        try {
            return select(decisionModel.decide(decisionRequest).choice(QUESTION_NAME));
        } catch (RuntimeException e) {
            log.warn("Failed to select a route, the default route will be used", e);
            return null;
        }
    }

    @Override
    public CompletableFuture<String> routeAsync(ChatModelRoutingRequest request) {
        if (request.routes().size() == 1) {
            return CompletableFuture.completedFuture(request.routes().get(0).name());
        }
        DecisionRequest decisionRequest;
        try {
            decisionRequest = toDecisionRequest(request);
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
        if (decisionRequest == null) {
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<DecisionResponse> source = decisionModel.decideAsync(decisionRequest);
        CompletableFuture<String> result = source.thenApply(response -> select(response.choice(QUESTION_NAME)))
                .exceptionally(error -> {
                    Throwable cause = unwrapCompletionException(error);
                    if (cause instanceof CancellationException cancellation) {
                        throw cancellation;
                    }
                    log.warn("Failed to select a route, the default route will be used", cause);
                    return null;
                });
        propagateCancellation(result, source);
        return result;
    }

    private DecisionRequest toDecisionRequest(ChatModelRoutingRequest request) {
        String userMessage = lastUserMessageText(request.chatRequest().messages());
        if (userMessage == null) {
            return null;
        }
        Map<String, String> options = new LinkedHashMap<>();
        for (ChatModelRoute route : request.routes()) {
            if (isNullOrBlank(route.description())) {
                throw new IllegalArgumentException(("Route '%s' has no description. "
                                + "%s decides based on the descriptions of the routes, so each route needs one")
                        .formatted(route.name(), getClass().getSimpleName()));
            }
            options.put(route.name(), route.description());
        }
        return DecisionRequest.builder()
                .input(userMessage)
                .question(QUESTION_NAME, ChoiceQuestion.of(question, options))
                .build();
    }

    private String select(ChoiceAnswer answer) {
        if (minProbability != null
                && !answer.probabilities().isEmpty()
                && answer.probabilityOf(answer.value()) < minProbability) {
            return null;
        }
        return answer.value();
    }

    private static String lastUserMessageText(List<ChatMessage> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) instanceof UserMessage userMessage) {
                String text = userMessage.contents().stream()
                        .filter(TextContent.class::isInstance)
                        .map(content -> ((TextContent) content).text())
                        .collect(joining("\n"));
                return text.isBlank() ? null : text;
            }
        }
        return null;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private DecisionModel decisionModel;
        private String question;
        private Double minProbability;

        /**
         * Sets the decision model that selects the route. Required.
         */
        public Builder decisionModel(DecisionModel decisionModel) {
            this.decisionModel = decisionModel;
            return this;
        }

        /**
         * Sets the question asked to choose between the routes, whose descriptions are the options.
         * <p>
         * Default value is {@value DecisionModelChatModelRouter#DEFAULT_QUESTION}
         */
        public Builder question(String question) {
            this.question = question;
            return this;
        }

        /**
         * Sets the minimum probability of the chosen route. Below it, the router does not select a route, so the
         * default route of the routing chat model is used, for example a larger model when the decision model is not
         * sure. Optional: by default, the chosen route is always used.
         */
        public Builder minProbability(Double minProbability) {
            this.minProbability = minProbability;
            return this;
        }

        public DecisionModelChatModelRouter build() {
            return new DecisionModelChatModelRouter(this);
        }
    }
}
