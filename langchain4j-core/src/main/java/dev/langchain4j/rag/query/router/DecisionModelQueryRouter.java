package dev.langchain4j.rag.query.router;

import static dev.langchain4j.internal.CompletableFutureUtils.propagateCancellation;
import static dev.langchain4j.internal.Exceptions.unwrapCompletionException;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureBetween;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.rag.query.router.LanguageModelQueryRouter.FallbackStrategy.DO_NOT_ROUTE;
import static java.util.Collections.emptyList;

import dev.langchain4j.Experimental;
import dev.langchain4j.exception.AsyncNotSupportedException;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.YesNoQuestion;
import dev.langchain4j.model.decision.response.DecisionResponse;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.rag.query.router.LanguageModelQueryRouter.FallbackStrategy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link QueryRouter} that uses a {@link DecisionModel} to decide which {@link ContentRetriever}s can help answer
 * the query.
 * <p>
 * For each retriever, the decision model answers a yes/no question ("Could this data source contain information that
 * helps answer the query?"), all in a single call. The query is routed to every retriever whose probability of "yes"
 * reaches the minimum probability (0.5 by default). When no retriever qualifies, no retrieval is performed, which is useful for queries that do
 * not need retrieval at all (e.g. "Hi!").
 * <pre>{@code
 * QueryRouter queryRouter = DecisionModelQueryRouter.builder()
 *         .decisionModel(decisionModel)
 *         .retrieverToDescription(Map.of(
 *                 hrRetriever, "HR policies: leave, benefits, expenses",
 *                 wikiRetriever, "Engineering wiki: services, deployments, on-call"))
 *         .build();
 * }</pre>
 * If the decision model fails, the {@link FallbackStrategy} applies, like in {@link LanguageModelQueryRouter}: by
 * default, no content is retrieved and a warning is logged.
 *
 * @since 1.21.0
 */
@Experimental
public class DecisionModelQueryRouter implements QueryRouter {

    private static final Logger log = LoggerFactory.getLogger(DecisionModelQueryRouter.class);

    /**
     * The default template of the question asked for each retriever.
     */
    public static final PromptTemplate DEFAULT_QUESTION_TEMPLATE = PromptTemplate.from(
            "Could the following data source contain information that helps answer the query?\n{{description}}");
    private static final double DEFAULT_MIN_PROBABILITY = 0.5;

    private final DecisionModel decisionModel;
    private final Map<String, ContentRetriever> retrievers;
    private final Map<String, YesNoQuestion> questions;
    private final double minProbability;
    private final FallbackStrategy fallbackStrategy;

    public DecisionModelQueryRouter(DecisionModel decisionModel, Map<ContentRetriever, String> retrieverToDescription) {
        this(builder().decisionModel(decisionModel).retrieverToDescription(retrieverToDescription));
    }

    protected DecisionModelQueryRouter(Builder builder) {
        this.decisionModel = ensureNotNull(builder.decisionModel, "decisionModel");
        ensureNotEmpty(builder.retrieverToDescription, "retrieverToDescription");
        PromptTemplate questionTemplate = getOrDefault(builder.questionTemplate, DEFAULT_QUESTION_TEMPLATE);
        if (!questionTemplate.template().contains("{{description}}")) {
            throw new IllegalArgumentException("The question template must contain {{description}}, but was: "
                    + questionTemplate.template());
        }
        this.minProbability = ensureBetween(
                getOrDefault(builder.minProbability, DEFAULT_MIN_PROBABILITY), 0, 1, "minProbability");
        this.fallbackStrategy = getOrDefault(builder.fallbackStrategy, DO_NOT_ROUTE);

        Map<String, ContentRetriever> retrievers = new LinkedHashMap<>();
        Map<String, YesNoQuestion> questions = new LinkedHashMap<>();
        int id = 1;
        for (Map.Entry<ContentRetriever, String> entry : builder.retrieverToDescription.entrySet()) {
            String name = "source" + id++;
            retrievers.put(name, ensureNotNull(entry.getKey(), "ContentRetriever"));
            String description = ensureNotBlank(entry.getValue(), "ContentRetriever description");
            questions.put(
                    name,
                    YesNoQuestion.of(questionTemplate
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
            return fallback(e);
        }
    }

    @Override
    public CompletableFuture<Collection<ContentRetriever>> routeAsync(Query query) {
        CompletableFuture<DecisionResponse> source;
        try {
            source = decisionModel.decideAsync(toRequest(query));
        } catch (Exception e) {
            try {
                return CompletableFuture.completedFuture(fallback(e));
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
                        return fallback(e);
                    }
                    throw cause instanceof RuntimeException re ? re : new CompletionException(cause);
                });
        propagateCancellation(result, source);
        return result;
    }

    private DecisionRequest toRequest(Query query) {
        return DecisionRequest.builder()
                .input(query.text())
                .questions(questions)
                .build();
    }

    private Collection<ContentRetriever> select(DecisionResponse response) {
        List<ContentRetriever> selected = new ArrayList<>();
        retrievers.forEach((name, retriever) -> {
            if (response.yesNo(name).isYes(minProbability)) {
                selected.add(retriever);
            }
        });
        return selected;
    }

    protected Collection<ContentRetriever> fallback(Exception e) {
        return switch (fallbackStrategy) {
            case DO_NOT_ROUTE -> {
                log.warn("Failed to route the query, no content will be retrieved", e);
                yield emptyList();
            }
            case ROUTE_TO_ALL -> {
                log.warn("Failed to route the query, it will be routed to all content retrievers", e);
                yield new ArrayList<>(retrievers.values());
            }
            default -> throw e instanceof RuntimeException re ? re : new RuntimeException(e);
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
         * Default value is {@link DecisionModelQueryRouter#DEFAULT_QUESTION_TEMPLATE}: "Could the following data
         * source contain information that helps answer the query?
{{description}}".
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

        public DecisionModelQueryRouter build() {
            return new DecisionModelQueryRouter(this);
        }
    }
}
