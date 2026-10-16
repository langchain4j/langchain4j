package dev.langchain4j.model.chat.router;

import static dev.langchain4j.internal.CompletableFutureUtils.propagateCancellation;
import static dev.langchain4j.internal.Exceptions.unwrapCompletionException;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureBetween;
import static dev.langchain4j.internal.ValidationUtils.ensureGreaterThanZero;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.AsyncNotSupportedException;
import dev.langchain4j.exception.ContentFilteredException;
import dev.langchain4j.internal.DecisionModelInputUtils;
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
import java.util.concurrent.CompletionException;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link ChatModelRouter} that uses a {@link DecisionModel} to select the route: the decision model chooses the
 * route whose description fits the last user message best.
 * <pre>{@code
 * ChatModel chatModel = RoutingChatModel.builder()
 *         .route("simple", "Greetings, short factual questions, simple lookups", smallModel)
 *         .route("complex", "Multi-step reasoning, code, analysis", largeModel)
 *         .router(new DecisionModelChatModelRouter(decisionModel))
 *         .defaultRoute("complex")
 *         .build();
 * }</pre>
 * A route without a description is described by its name.
 * <p>
 * The decision model receives the conversation of the request, as sent to the chat model: its system messages and its
 * last messages (10 by default, see {@link Builder#maxMessages(Integer)}), plus the last user message if it is older,
 * with tool calls and tool results, so that short follow-ups such as "yes, go ahead" are understood. Long messages are
 * shortened (see {@link Builder#maxMessageLength(Integer)}), and which messages are sent can be changed with
 * {@link Builder#messageFilter(Predicate)}. A request that ends with tool results usually stays on the route of the
 * request that called the tools, without asking the router.
 * In an AI Service, the last user message is the one after the prompt template and retrieved content were added to
 * it. Content other than text is represented by a marker, such as {@code [attached image]}, so that a route whose
 * description mentions images can be chosen for it.
 * <p>
 * The router selects the default route of the routing chat model ({@link ChatModelRoutingResult#defaultRoute()}) when
 * the request contains
 * no user message, and when the probability of the chosen route is below {@link Builder#minProbability(Double)}.
 * A minimum probability requires a decision model that reports probabilities: otherwise the call fails with an
 * {@link IllegalStateException}, whatever the {@link FallbackStrategy}. When the decision model fails or refuses to
 * choose a route, the {@link FallbackStrategy} applies: by default, the default route is used and a warning is
 * logged. When only one route can handle the request (for example, the only route supporting a JSON schema response format), that route is
 * selected without calling the decision model.
 * <p>
 * {@link #routeAsync(ChatModelRoutingRequest)} uses {@link DecisionModel#decideAsync(DecisionRequest)}, so it fails
 * with an {@link dev.langchain4j.exception.AsyncNotSupportedException} if the decision model does not support
 * asynchronous calls.
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
    private static final int DEFAULT_MAX_MESSAGES = 10;
    private static final int DEFAULT_MAX_MESSAGE_LENGTH = 2000;
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
    private final int maxMessageLength;
    private final Predicate<? super ChatMessage> messageFilter;

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
        this.maxMessages = ensureGreaterThanZero(getOrDefault(builder.maxMessages, DEFAULT_MAX_MESSAGES), "maxMessages");
        this.maxMessageLength = ensureGreaterThanZero(
                getOrDefault(builder.maxMessageLength, DEFAULT_MAX_MESSAGE_LENGTH), "maxMessageLength");
        this.messageFilter = getOrDefault(builder.messageFilter, message -> true);
    }

    @Override
    public void validate(List<ChatModelRoute> routes) {
        routeOptions(routes);
    }

    @Override
    public ChatModelRoutingResult route(ChatModelRoutingRequest request) {
        if (request.routes().size() == 1) {
            return ChatModelRoutingResult.route(request.routes().get(0).name());
        }
        DecisionRequest decisionRequest = toDecisionRequest(request);
        if (decisionRequest == null) {
            return ChatModelRoutingResult.defaultRoute();
        }
        DecisionResponse response;
        try {
            response = decisionModel.decide(decisionRequest);
        } catch (RuntimeException e) {
            return fallback(e);
        }
        return selectOrFallBack(response, request);
    }

    private ChatModelRoutingResult selectOrFallBack(DecisionResponse response, ChatModelRoutingRequest request) {
        if (response.isRefused(QUESTION_NAME)) {
            return fallback(new ContentFilteredException("The decision model refused to choose a route"));
        }
        return select(response.choice(QUESTION_NAME), request.routes());
    }

    @Override
    public CompletableFuture<ChatModelRoutingResult> routeAsync(ChatModelRoutingRequest request) {
        if (request.routes().size() == 1) {
            return CompletableFuture.completedFuture(ChatModelRoutingResult.route(request.routes().get(0).name()));
        }
        DecisionRequest decisionRequest;
        try {
            decisionRequest = toDecisionRequest(request);
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
        if (decisionRequest == null) {
            return CompletableFuture.completedFuture(ChatModelRoutingResult.defaultRoute());
        }
        CompletableFuture<DecisionResponse> source;
        try {
            source = decisionModel.decideAsync(decisionRequest);
        } catch (RuntimeException e) {
            source = CompletableFuture.failedFuture(e);
        }
        CompletableFuture<ChatModelRoutingResult> result = source.handle((response, error) -> {
            if (error == null) {
                return selectOrFallBack(response, request);
            }
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

    private ChatModelRoutingResult fallback(RuntimeException error) {
        if (fallbackStrategy == FallbackStrategy.FAIL) {
            throw error;
        }
        log.warn("Failed to select a route, the default route will be used", error);
        return ChatModelRoutingResult.defaultRoute();
    }

    private DecisionRequest toDecisionRequest(ChatModelRoutingRequest request) {
        List<ChatMessage> messages = request.chatRequest().messages();
        int lastUserMessage = lastUserMessage(messages);
        if (lastUserMessage < 0 || DecisionModelInputUtils.text(messages.get(lastUserMessage)).isBlank()) {
            return null;
        }
        List<ChatMessage> conversation = DecisionModelInputUtils.window(messages, messageFilter, maxMessages, maxMessageLength);
        if (conversation.isEmpty()) {
            return null;
        }
        return DecisionRequest.builder()
                .question(QUESTION_NAME, toQuestion(request.routes()))
                .inputMessages(conversation)
                .build();
    }

    private record RouteOption(ChatModelRoute route, String description) {}

    /**
     * One option per description of each route: a route with one description (or none) is the option named like the
     * route; a route with several descriptions has one option per description, named {@code <route>#<n>}.
     */
    private ChoiceQuestion toQuestion(List<ChatModelRoute> routes) {
        ChoiceQuestion.Builder choice = ChoiceQuestion.builder().text(question);
        routeOptions(routes).forEach((name, option) -> {
            if (option.description() == null) {
                choice.option(name);
            } else {
                choice.option(name, option.description());
            }
        });
        return choice.build();
    }

    private static Map<String, RouteOption> routeOptions(List<ChatModelRoute> routes) {
        Map<String, RouteOption> options = new LinkedHashMap<>();
        for (ChatModelRoute route : routes) {
            List<String> descriptions = route.descriptions();
            if (descriptions.size() <= 1) {
                addOption(options, route.name(), new RouteOption(route, descriptions.isEmpty() ? null : descriptions.get(0)));
            } else {
                for (int i = 0; i < descriptions.size(); i++) {
                    addOption(options, route.name() + "#" + (i + 1), new RouteOption(route, descriptions.get(i)));
                }
            }
        }
        return options;
    }

    private static void addOption(Map<String, RouteOption> options, String name, RouteOption option) {
        if (options.putIfAbsent(name, option) != null) {
            throw new IllegalArgumentException("The route names and descriptions give the option '%s' twice. "
                    .formatted(name) + "Rename the route '%s'".formatted(option.route().name()));
        }
    }

    /**
     * Selects the route with the highest probability, summed over its options.
     */
    private ChatModelRoutingResult select(ChoiceAnswer answer, List<ChatModelRoute> routes) {
        Map<String, RouteOption> options = routeOptions(routes);
        if (minProbability != null && answer.probabilities().isEmpty()) {
            throw new IllegalStateException("minProbability is set, but the decision model reported no probabilities, "
                    + "so the default route would always be used. Use a decision model that reports probabilities, "
                    + "or remove minProbability");
        }
        if (answer.probabilities().isEmpty()) {
            return ChatModelRoutingResult.route(options.get(answer.value()).route().name());
        }
        Map<String, Double> routeProbabilities = new LinkedHashMap<>();
        options.forEach((name, option) -> routeProbabilities.merge(
                option.route().name(), answer.probabilities().getOrDefault(name, 0.0), Double::sum));
        Map.Entry<String, Double> best = routeProbabilities.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .orElseThrow();
        if (minProbability != null && best.getValue() < minProbability) {
            return ChatModelRoutingResult.defaultRoute();
        }
        return ChatModelRoutingResult.route(best.getKey());
    }

    private static int lastUserMessage(List<ChatMessage> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) instanceof UserMessage) {
                return i;
            }
        }
        return -1;
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
        private Integer maxMessageLength;
        private Predicate<? super ChatMessage> messageFilter;

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
         * It is a plain question rather than a template: all routes are the options of a single choice question, so
         * there is nothing to insert into the question.
         * <p>
         * Default value is {@value DecisionModelChatModelRouter#DEFAULT_QUESTION}.
         */
        public Builder question(String question) {
            this.question = question;
            return this;
        }

        /**
         * Sets the minimum probability of the chosen route. Below it, the router does not select a route, so the
         * default route of the routing chat model is used, for example a larger model when the decision model is not
         * sure. Requires a decision model that reports probabilities. Optional: by default, the chosen route is always
         * used.
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
         * Sets the maximum number of messages of the conversation that the decision model receives: the last messages
         * of the request, plus the last user message if it is older. An AI message with tool calls and each tool result
         * count as one message each. Tool results whose AI message does not fit are left out. With parallel tool calls,
         * set it higher than the number of tool calls in one round. With 1, system messages are still sent; leave them
         * out with {@link #messageFilter(Predicate)}. System messages are sent in addition and do not count. Previous
         * messages help with follow-ups whose meaning depends on them; too many can make an older topic outweigh the
         * last message.
         * <p>
         * Default value is {@value DecisionModelChatModelRouter#DEFAULT_MAX_MESSAGES}.
         */
        public Builder maxMessages(Integer maxMessages) {
            this.maxMessages = maxMessages;
            return this;
        }

        /**
         * Sets the maximum length, in characters, of the text of each message the decision model receives, other than
         * system messages, and of the arguments of each tool call. A longer text is shortened in the middle:
         * its beginning and its end are kept, with a note of about 90 characters saying how many characters were left
         * out. Long tool results and user messages with retrieved content are
         * the usual cases. As a rough guide, 4 characters make one token in English text.
         * <p>
         * Default value is {@value DecisionModelChatModelRouter#DEFAULT_MAX_MESSAGE_LENGTH}.
         * @since 1.23.0
         */
        public Builder maxMessageLength(Integer maxMessageLength) {
            this.maxMessageLength = maxMessageLength;
            return this;
        }

        /**
         * Sets which messages of the conversation the decision model receives; it also applies to the last user
         * message. For example,
         * {@code message -> !(message instanceof SystemMessage)} leaves out the system message, and
         * {@code message -> !(message instanceof ToolExecutionResultMessage)} leaves out tool results, which can be
         * large.
         * <p>
         * By default, all messages are sent: system messages, user messages, AI messages with their tool calls, and
         * tool results.
         * @since 1.23.0
         */
        public Builder messageFilter(Predicate<? super ChatMessage> messageFilter) {
            this.messageFilter = messageFilter;
            return this;
        }

        public DecisionModelChatModelRouter build() {
            return new DecisionModelChatModelRouter(this);
        }
    }
}
