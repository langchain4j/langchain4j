package dev.langchain4j.model.chat.router;

import static dev.langchain4j.internal.CompletableFutureUtils.propagateCancellation;
import static dev.langchain4j.internal.Exceptions.unwrapCompletionException;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureBetween;
import static dev.langchain4j.internal.ValidationUtils.ensureGreaterThanZero;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.util.stream.Collectors.joining;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.AsyncNotSupportedException;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.request.ChoiceQuestion;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.response.ChoiceAnswer;
import dev.langchain4j.model.decision.response.DecisionResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
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
 * Every route needs a description; this is checked when the routing chat model is created.
 * <p>
 * The decision model receives the last user message of the request, as sent to the chat model. In an AI Service, this
 * is the user message after the prompt template, retrieved content and output format instructions were added to it.
 * Content other than text is represented by a marker, such as {@code [attached image]}, so that a route whose
 * description mentions images can be chosen for it. To also take the previous messages of the conversation into
 * account, which helps with short follow-ups such as "yes, go ahead", set {@link Builder#maxMessages(Integer)}.
 * <p>
 * The router returns {@code null} (so the default route of the routing chat model is used) when the request contains
 * no user message, and when the probability of the chosen route is below {@link Builder#minProbability(Double)} or the
 * decision model reports no probabilities while a minimum is set. When the decision model fails, the
 * {@link FallbackStrategy} applies: by default, the default route is used and a warning is logged. When only one
 * route can handle the request (for example, the only route supporting a JSON schema response format), that route is
 * selected without calling the decision model.
 * <p>
 * {@link #routeAsync(ChatModelRoutingRequest)} uses {@link DecisionModel#decideAsync(DecisionRequest)}. If the
 * decision model does not support asynchronous calls, the routing chat models call
 * {@link #route(ChatModelRoutingRequest)} on the default executor instead.
 *
 * @since 1.21.0
 */
@Experimental
public class DecisionModelChatModelRouter implements ChatModelRouter {

    private static final Logger log = LoggerFactory.getLogger(DecisionModelChatModelRouter.class);

    /**
     * The default question asked to choose between the routes.
     */
    public static final String DEFAULT_QUESTION = "Which model should handle this request?";
    private static final String QUESTION_NAME = "route";

    /**
     * What the router does when the decision model fails.
     */
    public enum FallbackStrategy {

        /**
         * Do not select a route, so the default route of the routing chat model is used, and log a warning.
         */
        DEFAULT_ROUTE,

        /**
         * Fail the request with the error of the decision model.
         */
        FAIL
    }

    private final DecisionModel decisionModel;
    private final String question;
    private final Double minProbability;
    private final FallbackStrategy fallbackStrategy;
    private final int maxMessages;

    public DecisionModelChatModelRouter(DecisionModel decisionModel) {
        this(builder().decisionModel(decisionModel));
    }

    protected DecisionModelChatModelRouter(Builder builder) {
        this.decisionModel = ensureNotNull(builder.decisionModel, "decisionModel");
        this.question = ensureNotBlank(getOrDefault(builder.question, DEFAULT_QUESTION), "question");
        this.minProbability = builder.minProbability == null
                ? null
                : ensureBetween(builder.minProbability, 0, 1, "minProbability");
        this.fallbackStrategy = getOrDefault(builder.fallbackStrategy, FallbackStrategy.DEFAULT_ROUTE);
        this.maxMessages = ensureGreaterThanZero(getOrDefault(builder.maxMessages, 1), "maxMessages");
    }

    @Override
    public void validate(List<ChatModelRoute> routes) {
        for (ChatModelRoute route : routes) {
            if (isNullOrBlank(route.description())) {
                throw new IllegalArgumentException(("Route '%s' has no description. "
                                + "%s decides based on the descriptions of the routes, so each route needs one")
                        .formatted(route.name(), getClass().getSimpleName()));
            }
        }
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
            return fallback(e);
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
                    if (cause instanceof CancellationException
                            || cause instanceof AsyncNotSupportedException
                            || !(cause instanceof RuntimeException)) {
                        throw cause instanceof RuntimeException re ? re : new CompletionException(cause);
                    }
                    return fallback((RuntimeException) cause);
                });
        propagateCancellation(result, source);
        return result;
    }

    private String fallback(RuntimeException error) {
        if (fallbackStrategy == FallbackStrategy.FAIL) {
            throw error;
        }
        log.warn("Failed to select a route, the default route will be used", error);
        return null;
    }

    private DecisionRequest toDecisionRequest(ChatModelRoutingRequest request) {
        Object input = input(request.chatRequest().messages());
        if (input == null) {
            return null;
        }
        validate(request.routes());
        Map<String, String> options = new LinkedHashMap<>();
        request.routes().forEach(route -> options.put(route.name(), route.description()));
        return DecisionRequest.builder()
                .input(input)
                .question(QUESTION_NAME, ChoiceQuestion.of(question, options))
                .build();
    }

    private String select(ChoiceAnswer answer) {
        if (minProbability != null
                && (answer.probabilities().isEmpty() || answer.probabilityOf(answer.value()) < minProbability)) {
            return null;
        }
        return answer.value();
    }

    /**
     * The last user message or, with {@code maxMessages > 1}, the last messages of the conversation up to and
     * including the last user message. Returns {@code null} if there is no user message with content.
     */
    private Object input(List<ChatMessage> messages) {
        int lastUserMessage = -1;
        for (int i = messages.size() - 1; i >= 0 && lastUserMessage < 0; i--) {
            if (messages.get(i) instanceof UserMessage) {
                lastUserMessage = i;
            }
        }
        if (lastUserMessage < 0) {
            return null;
        }
        String userMessage = text(messages.get(lastUserMessage));
        if (userMessage.isBlank()) {
            return null;
        }
        if (maxMessages == 1) {
            return userMessage;
        }
        List<Map<String, String>> conversation = new ArrayList<>();
        for (ChatMessage message : messages.subList(0, lastUserMessage + 1)) {
            String text = text(message);
            if (!text.isBlank()) {
                conversation.add(Map.of("role", message instanceof UserMessage ? "user" : "assistant", "text", text));
            }
        }
        return conversation.subList(Math.max(0, conversation.size() - maxMessages), conversation.size());
    }

    private static String text(ChatMessage message) {
        if (message instanceof UserMessage userMessage) {
            return userMessage.contents().stream()
                    .map(content -> content instanceof TextContent textContent
                            ? textContent.text()
                            : "[attached " + content.type().name().toLowerCase(Locale.ROOT) + "]")
                    .collect(joining("\n"));
        }
        if (message instanceof AiMessage aiMessage && aiMessage.text() != null) {
            return aiMessage.text();
        }
        return "";
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private DecisionModel decisionModel;
        private String question;
        private Double minProbability;
        private FallbackStrategy fallbackStrategy;
        private Integer maxMessages;

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
         * Default value is {@value DecisionModelChatModelRouter#DEFAULT_QUESTION}.
         */
        public Builder question(String question) {
            this.question = question;
            return this;
        }

        /**
         * Sets the minimum probability of the chosen route. Below it, or if the decision model reports no
         * probabilities, the router does not select a route, so the default route of the routing chat model is used,
         * for example a larger model when the decision model is not sure. Optional: by default, the chosen route is
         * always used.
         */
        public Builder minProbability(Double minProbability) {
            this.minProbability = minProbability;
            return this;
        }

        /**
         * Sets what happens when the decision model fails.
         * <p>
         * Default value is {@link FallbackStrategy#DEFAULT_ROUTE}.
         */
        public Builder fallbackStrategy(FallbackStrategy fallbackStrategy) {
            this.fallbackStrategy = fallbackStrategy;
            return this;
        }

        /**
         * Sets the maximum number of messages of the conversation (user messages and text responses of the AI, up to
         * and including the last user message) that the decision model receives. Values greater than 1 help with
         * follow-ups whose meaning depends on the previous messages, but make the decision slower.
         * <p>
         * Default value is 1: only the last user message.
         */
        public Builder maxMessages(Integer maxMessages) {
            this.maxMessages = maxMessages;
            return this;
        }

        public DecisionModelChatModelRouter build() {
            return new DecisionModelChatModelRouter(this);
        }
    }
}
