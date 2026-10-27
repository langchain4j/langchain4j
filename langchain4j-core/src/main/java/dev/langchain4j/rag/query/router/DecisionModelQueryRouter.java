package dev.langchain4j.rag.query.router;

import static dev.langchain4j.internal.CompletableFutureUtils.propagateCancellation;
import static dev.langchain4j.internal.Exceptions.unwrapCompletionException;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureBetween;
import static dev.langchain4j.internal.ValidationUtils.ensureGreaterThanZero;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.util.Collections.emptyList;

import dev.langchain4j.Experimental;
import dev.langchain4j.internal.DecisionModelInputUtils;
import dev.langchain4j.exception.AsyncNotSupportedException;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.YesNoQuestion;
import dev.langchain4j.model.decision.response.DecisionResponse;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Predicate;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.rag.query.Metadata;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link QueryRouter} that uses a {@link DecisionModel} to decide which {@link ContentRetriever}s can help answer
 * the query.
 * <p>
 * For each retriever, the decision model answers a yes/no question about whether the retriever, described by its
 * description, could help answer the query (see {@link #DEFAULT_QUESTION_TEMPLATE}), all in a single call. The query is
 * routed to every retriever whose probability of "yes" reaches the minimum probability (0.5 by default). When no
 * retriever qualifies, no retrieval is performed, which is useful for queries that do not need retrieval at all
 * (e.g. "Hi!"). The decision model receives the query ({@code {"query": ...}}) and, when the query comes from a
 * conversation, the system message and the previous messages ({@code "messages"}, see
 * {@link Builder#maxMessages(Integer)}, {@link Builder#maxMessageLength(Integer)} and
 * {@link Builder#messageFilter(Predicate)}), including tool calls and tool results, so that follow-up questions such
 * as "and for contractors?" are understood.
 * <pre>{@code
 * QueryRouter queryRouter = DecisionModelQueryRouter.builder()
 *         .decisionModel(decisionModel)
 *         .retrieverToDescription(Map.of(
 *                 hrRetriever, "HR policies: leave, benefits, expenses",
 *                 wikiRetriever, "Engineering wiki: services, deployments, on-call"))
 *         .build();
 * }</pre>
 * If the decision model fails or refuses to answer, the {@link FallbackStrategy} applies: by default, no content is
 * retrieved and a warning is logged.
 *
 * @since 1.21.0
 */
@Experimental
public class DecisionModelQueryRouter implements QueryRouter {

    private static final Logger log = LoggerFactory.getLogger(DecisionModelQueryRouter.class);

    /**
     * The default template of the question asked for each retriever:
     * {@code "Could the following data source contain information that helps answer the query?\n{{description}}"}.
     */
    public static final PromptTemplate DEFAULT_QUESTION_TEMPLATE = PromptTemplate.from(
            "Could the following data source contain information that helps answer the query?\n{{description}}");
    private static final double DEFAULT_MIN_PROBABILITY = 0.5;
    private static final int DEFAULT_MAX_MESSAGES = 10;
    private static final int DEFAULT_MAX_MESSAGE_LENGTH = 2000;

    /**
     * What the router does when the decision model fails.
     */
    public enum FallbackStrategy {

        /**
         * The query is not routed to any retriever, so no content is retrieved, and a warning is logged.
         */
        DO_NOT_ROUTE,

        /**
         * The query is routed to all retrievers, and a warning is logged.
         */
        ROUTE_TO_ALL,

        /**
         * The error of the decision model is rethrown, so the request fails.
         */
        FAIL
    }

    private final DecisionModel decisionModel;
    private final Map<String, ContentRetriever> retrievers;
    private final Map<String, YesNoQuestion> questions;
    private final double minProbability;
    private final FallbackStrategy fallbackStrategy;
    private final int maxMessages;
    private final int maxMessageLength;
    private final Predicate<? super ChatMessage> messageFilter;

    public DecisionModelQueryRouter(DecisionModel decisionModel, Map<ContentRetriever, String> retrieverToDescription) {
        this(builder().decisionModel(decisionModel).retrieverToDescription(retrieverToDescription));
    }

    protected DecisionModelQueryRouter(Builder builder) {
        this.decisionModel = ensureNotNull(builder.decisionModel, "decisionModel");
        ensureNotEmpty(builder.retrieverToDescription, "retrieverToDescription");
        PromptTemplate questionTemplate = getOrDefault(builder.questionTemplate, DEFAULT_QUESTION_TEMPLATE);
        validate(questionTemplate);
        this.minProbability = ensureBetween(
                getOrDefault(builder.minProbability, DEFAULT_MIN_PROBABILITY), 0, 1, "minProbability");
        this.fallbackStrategy = getOrDefault(builder.fallbackStrategy, FallbackStrategy.DO_NOT_ROUTE);
        this.maxMessages = ensureGreaterThanZero(getOrDefault(builder.maxMessages, DEFAULT_MAX_MESSAGES), "maxMessages");
        this.maxMessageLength = ensureGreaterThanZero(
                getOrDefault(builder.maxMessageLength, DEFAULT_MAX_MESSAGE_LENGTH), "maxMessageLength");
        this.messageFilter = getOrDefault(builder.messageFilter, message -> true);

        Map<String, ContentRetriever> retrievers = new LinkedHashMap<>();
        Map<String, YesNoQuestion> questions = new LinkedHashMap<>();
        int id = 1;
        for (Map.Entry<ContentRetriever, String> entry : builder.retrieverToDescription.entrySet()) {
            String name = "source" + id++;
            retrievers.put(name, ensureNotNull(entry.getKey(), "ContentRetriever"));
            String description = ensureNotBlank(entry.getValue(), "ContentRetriever description");
            questions.put(
                    name,
                    YesNoQuestion.from(questionTemplate
                            .apply(Map.of("description", description))
                            .text()));
        }
        this.retrievers = retrievers;
        this.questions = questions;
    }

    @Override
    public Collection<ContentRetriever> route(Query query) {
        try {
            return select(decisionModel.decide(toRequest(query)));
        } catch (Exception e) {
            return fallback(query, e);
        }
    }

    @Override
    public CompletableFuture<Collection<ContentRetriever>> routeAsync(Query query) {
        CompletableFuture<DecisionResponse> source;
        try {
            source = decisionModel.decideAsync(toRequest(query));
        } catch (Exception e) {
            try {
                return CompletableFuture.completedFuture(fallback(query, e));
            } catch (RuntimeException fallbackError) {
                return CompletableFuture.failedFuture(fallbackError);
            }
        }
        CompletableFuture<Collection<ContentRetriever>> result = source.thenApply(this::select)
                .exceptionally(error -> {
                    Throwable cause = unwrapCompletionException(error);
                    // AsyncNotSupportedException is propagated, so that the caller can call route() instead
                    if (cause instanceof Exception e
                            && !(cause instanceof CancellationException)
                            && !(cause instanceof AsyncNotSupportedException)) {
                        return fallback(query, e);
                    }
                    throw cause instanceof RuntimeException re ? re : new CompletionException(cause);
                });
        propagateCancellation(result, source);
        return result;
    }

    private static void validate(PromptTemplate template) {
        if (!template.template().contains("{{description}}")) {
            throw new IllegalArgumentException(
                    "The question template must contain {{description}}, but was: " + template.template());
        }
        try {
            template.apply(Map.of("description", "description"));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(
                    "The question template can only use the {{description}} variable, but was: "
                            + template.template(),
                    e);
        }
    }

    private DecisionRequest toRequest(Query query) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("query", query.text());
        List<ChatMessage> previousMessages = previousMessages(query);
        if (!previousMessages.isEmpty()) {
            input.put("messages", DecisionModelInputUtils.transcript(previousMessages).messages());
        }
        return DecisionRequest.builder().inputFields(input).questions(questions).build();
    }

    /**
     * The system message and the last {@code maxMessages - 1} messages of the conversation before the query, if the
     * query comes from one.
     */
    private List<ChatMessage> previousMessages(Query query) {
        Metadata metadata = query.metadata();
        if (metadata == null) {
            return List.of();
        }
        List<ChatMessage> messages = new ArrayList<>();
        if (metadata.systemMessage() != null) {
            messages.add(metadata.systemMessage());
        }
        if (metadata.chatMemory() != null) {
            for (ChatMessage message : metadata.chatMemory()) {
                if (!(message instanceof SystemMessage) || metadata.systemMessage() == null) {
                    messages.add(message);
                }
            }
            if (!messages.isEmpty()
                    && messages.get(messages.size() - 1) instanceof UserMessage userMessage
                    && DecisionModelInputUtils.text(userMessage).equals(query.text())) {
                messages.remove(messages.size() - 1);
            }
        }
        return DecisionModelInputUtils.window(messages, messageFilter, maxMessages - 1, maxMessageLength);
    }

    private Collection<ContentRetriever> select(DecisionResponse response) {
        List<ContentRetriever> selected = new ArrayList<>();
        retrievers.forEach((name, retriever) -> {
            if (response.yesNo(name).isYes(minProbability)) {
                selected.add(retriever);
            }
        });
        if (log.isDebugEnabled()) {
            retrievers.keySet().forEach(name -> log.debug(
                    "Retriever {} (question '{}'): probability {}",
                    name,
                    questions.get(name).text(),
                    response.yesNo(name).probability()));
        }
        return selected;
    }

    protected Collection<ContentRetriever> fallback(Query query, Exception e) {
        return switch (fallbackStrategy) {
            case DO_NOT_ROUTE -> {
                log.warn("Failed to route the query, no content will be retrieved", e);
                yield emptyList();
            }
            case ROUTE_TO_ALL -> {
                log.warn("Failed to route the query, it will be routed to all content retrievers", e);
                yield new ArrayList<>(retrievers.values());
            }
            case FAIL -> throw e instanceof RuntimeException re ? re : new RuntimeException(e);
        };
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private DecisionModel decisionModel;
        private Map<ContentRetriever, String> retrieverToDescription;
        private PromptTemplate questionTemplate;
        private Double minProbability;
        private FallbackStrategy fallbackStrategy;
        private Integer maxMessages;
        private Integer maxMessageLength;
        private Predicate<? super ChatMessage> messageFilter;

        /**
         * Sets the decision model that decides which retrievers to use. Required.
         */
        public Builder decisionModel(DecisionModel decisionModel) {
            this.decisionModel = decisionModel;
            return this;
        }

        /**
         * Sets the retrievers to route to, each with a description of the content it can retrieve. Required.
         */
        public Builder retrieverToDescription(Map<ContentRetriever, String> retrieverToDescription) {
            this.retrieverToDescription = retrieverToDescription;
            return this;
        }

        /**
         * Sets the template of the yes/no question asked for each retriever, which must contain the
         * {@code {{description}}} variable (the description of the retriever). The query is the input.
         * <p>
         * Default value is {@link DecisionModelQueryRouter#DEFAULT_QUESTION_TEMPLATE}.
         */
        public Builder questionTemplate(PromptTemplate questionTemplate) {
            this.questionTemplate = questionTemplate;
            return this;
        }

        /**
         * Sets the minimum probability of "yes" for a retriever to be used.
         * <p>
         * Default value is {@value DecisionModelQueryRouter#DEFAULT_MIN_PROBABILITY}.
         */
        public Builder minProbability(Double minProbability) {
            this.minProbability = minProbability;
            return this;
        }

        /**
         * Sets what happens when the decision model fails.
         * <p>
         * Default value is {@link FallbackStrategy#DO_NOT_ROUTE}.
         */
        public Builder fallbackStrategy(FallbackStrategy fallbackStrategy) {
            this.fallbackStrategy = fallbackStrategy;
            return this;
        }

        /**
         * Sets how many of the last messages of the conversation are taken into account, including the query itself:
         * the previous messages (plus the previous user message, if it is older) are sent together with the query, so
         * that follow-up questions are understood. An AI message with tool calls and each tool result count as one
         * message each. Tool results whose AI message does not fit are left out. With parallel tool calls, set it
         * higher than the number of tool calls in one round. The previous messages are only sent when the query comes
         * from a conversation with a chat memory. The system message, if any, is sent in addition and does not count.
         * <p>
         * Default value is {@value DecisionModelQueryRouter#DEFAULT_MAX_MESSAGES}: the query and the 9 previous
         * messages. More messages can make an older topic of the conversation outweigh the query. When the queries are
         * already made self-contained, for example by a
         * {@link dev.langchain4j.rag.query.transformer.CompressingQueryTransformer}, set it to 1. The system message is
         * still sent then; leave it out with {@link #messageFilter(Predicate)}.
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
         * Default value is {@value DecisionModelQueryRouter#DEFAULT_MAX_MESSAGE_LENGTH}.
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

        public DecisionModelQueryRouter build() {
            return new DecisionModelQueryRouter(this);
        }
    }
}
