package dev.langchain4j.rag;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.aggregator.ContentAggregator;
import dev.langchain4j.rag.content.aggregator.DefaultContentAggregator;
import dev.langchain4j.rag.content.injector.ContentInjector;
import dev.langchain4j.rag.content.injector.DefaultContentInjector;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Metadata;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.rag.query.router.DefaultQueryRouter;
import dev.langchain4j.rag.query.router.QueryRouter;
import dev.langchain4j.rag.query.transformer.DefaultQueryTransformer;
import dev.langchain4j.rag.query.transformer.QueryTransformer;
import lombok.Builder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.util.Collections.*;
import static java.util.concurrent.CompletableFuture.allOf;
import static java.util.concurrent.CompletableFuture.supplyAsync;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.*;

/**
 * The default implementation of {@link RetrievalAugmentor} intended to be suitable for the majority of use cases.
 * <br>
 * <br>
 * It's important to note that while efforts will be made to avoid breaking changes,
 * the default behavior of this class may be updated in the future if it's found
 * that the current behavior does not adequately serve the majority of use cases.
 * Such changes would be made to benefit both current and future users.
 * <br>
 * <br>
 * This implementation is inspired by <a href="https://blog.langchain.dev/deconstructing-rag">this article</a>
 * and <a href="https://arxiv.org/abs/2312.10997">this paper</a>.
 * It is recommended to review these resources for a better understanding of the concept.
 * <br>
 * <br>
 * This implementation orchestrates the flow between the following base components:
 * <pre>
 * - {@link QueryTransformer}
 * - {@link QueryRouter}
 * - {@link ContentRetriever}
 * - {@link ContentAggregator}
 * - {@link ContentInjector}
 * </pre>
 * Visual representation of this flow can be found
 * <a href="https://docs.langchain4j.dev/img/advanced-rag.png">here</a>.
 * <br>
 * For each base component listed above, we offer several ready-to-use implementations,
 * each based on a recognized approach.
 * We intend to introduce more such implementations over time and welcome your contributions.
 * <br>
 * <br>
 * The flow is as follows:
 * <br>
 * 1. A {@link Query} (derived from an original {@link UserMessage}) is transformed
 * using a {@link QueryTransformer} into one or multiple {@link Query}s.
 * <br>
 * 2. Each {@link Query} is routed to the appropriate {@link ContentRetriever} using a {@link QueryRouter}.
 * Each {@link ContentRetriever} retrieves one or multiple {@link Content}s using a {@link Query}.
 * <br>
 * 3. All {@link Content}s retrieved by all {@link ContentRetriever}s using all {@link Query}s are
 * aggregated (fused/re-ranked/filtered/etc.) into a final list of {@link Content}s using a {@link ContentAggregator}.
 * <br>
 * 4. Lastly, a final list of {@link Content}s is injected into the original {@link UserMessage}
 * using a {@link ContentInjector}.
 * <br>
 * <br>
 * By default, each base component (except for {@link ContentRetriever}, which needs to be provided by you)
 * is initialized with a sensible default implementation:
 * <pre>
 * - {@link DefaultQueryTransformer}
 * - {@link DefaultQueryRouter}
 * - {@link DefaultContentAggregator}
 * - {@link DefaultContentInjector}
 * </pre>
 * Nonetheless, you are encouraged to use one of the advanced ready-to-use implementations or create a custom one.
 * <br>
 * <br>
 * When there is only a single {@link Query} and a single {@link ContentRetriever},
 * query routing and content retrieval are performed in the same thread.
 * Otherwise, an {@link Executor} is used to parallelize the processing.
 * By default, a modified (keepAliveTime is 1 second instead of 60 seconds) {@link Executors#newCachedThreadPool()}
 * is used, but you can provide a custom {@link Executor} instance.
 *
 * @see DefaultQueryTransformer
 * @see DefaultQueryRouter
 * @see DefaultContentAggregator
 * @see DefaultContentInjector
 */
public class DefaultRetrievalAugmentor implements RetrievalAugmentor {

    private static final Logger log = LoggerFactory.getLogger(DefaultRetrievalAugmentor.class);

    private final QueryTransformer queryTransformer;
    private final QueryRouter queryRouter;
    private final ContentAggregator contentAggregator;
    private final ContentInjector contentInjector;
    private final Executor executor;

    @Builder
    public DefaultRetrievalAugmentor(QueryTransformer queryTransformer,
                                     QueryRouter queryRouter,
                                     ContentAggregator contentAggregator,
                                     ContentInjector contentInjector,
                                     Executor executor) {
        this.queryTransformer = getOrDefault(queryTransformer, DefaultQueryTransformer::new);
        this.queryRouter = ensureNotNull(queryRouter, "queryRouter");
        this.contentAggregator = getOrDefault(contentAggregator, DefaultContentAggregator::new);
        this.contentInjector = getOrDefault(contentInjector, DefaultContentInjector::new);
        this.executor = getOrDefault(executor, DefaultRetrievalAugmentor::createDefaultExecutor);
    }

    private static ExecutorService createDefaultExecutor() {
        return new ThreadPoolExecutor(
                0, Integer.MAX_VALUE,
                1, SECONDS,
                new SynchronousQueue<>()
        );
    }

    /**
     * @deprecated use {@link #augment(AugmentationRequest)} instead.
     */
    @Override
    @Deprecated
    public UserMessage augment(UserMessage userMessage, Metadata metadata) {
        AugmentationRequest augmentationRequest = new AugmentationRequest(userMessage, metadata);
        return (UserMessage) augment(augmentationRequest).chatMessage();
    }

    @Override
    public AugmentationResult augment(AugmentationRequest augmentationRequest) {

        ChatMessage chatMessage = augmentationRequest.chatMessage();
        Metadata metadata = augmentationRequest.metadata();

        Query originalQuery = Query.from(chatMessage.text(), metadata);

        Collection<Query> queries = queryTransformer.transform(originalQuery);
        logQueries(originalQuery, queries);

        Map<Query, Collection<List<Content>>> queryToContents = process(queries);

        List<Content> contents = contentAggregator.aggregate(queryToContents);
        log(queryToContents, contents);

        ChatMessage augmentedChatMessage = contentInjector.inject(contents, chatMessage);
        log(augmentedChatMessage);

        return AugmentationResult.builder()
                .chatMessage(augmentedChatMessage)
                .contents(contents)
                .build();
    }

    private Map<Query, Collection<List<Content>>> process(Collection<Query> queries) {
        if (queries.size() == 1) {
            Query query = queries.iterator().next();
            Collection<ContentRetriever> retrievers = queryRouter.route(query);
            if (retrievers.size() == 1) {
                ContentRetriever contentRetriever = retrievers.iterator().next();
                List<Content> contents = contentRetriever.retrieve(query);
                return singletonMap(query, singletonList(contents));
            } else if (retrievers.size() > 1) {
                Collection<List<Content>> contents = retrieveFromAll(retrievers, query).join();
                return singletonMap(query, contents);
            } else {
                return emptyMap();
            }
        } else if (queries.size() > 1) {
            Map<Query, CompletableFuture<Collection<List<Content>>>> queryToFutureContents = new ConcurrentHashMap<>();
            queries.forEach(query -> {
                CompletableFuture<Collection<List<Content>>> futureContents =
                        supplyAsync(() -> {
                                    Collection<ContentRetriever> retrievers = queryRouter.route(query);
                                    log(query, retrievers);
                                    return retrievers;
                                },
                                executor
                        ).thenCompose(retrievers -> retrieveFromAll(retrievers, query));
                queryToFutureContents.put(query, futureContents);
            });
            return join(queryToFutureContents);
        } else {
            return emptyMap();
        }
    }

    private CompletableFuture<Collection<List<Content>>> retrieveFromAll(Collection<ContentRetriever> retrievers,
                                                                         Query query) {
        List<CompletableFuture<List<Content>>> futureContents = retrievers.stream()
                .map(retriever -> supplyAsync(() -> retrieve(retriever, query), executor))
                .collect(toList());

        return allOf(futureContents.toArray(new CompletableFuture[0]))
                .thenApply(ignored ->
                        futureContents.stream()
                                .map(CompletableFuture::join)
                                .collect(toList())
                );
    }

    private static List<Content> retrieve(ContentRetriever retriever, Query query) {
        List<Content> contents = retriever.retrieve(query);
        log(query, retriever, contents);
        return contents;
    }

    private static Map<Query, Collection<List<Content>>> join(
            Map<Query, CompletableFuture<Collection<List<Content>>>> queryToFutureContents) {
        return allOf(queryToFutureContents.values().toArray(new CompletableFuture[0]))
                .thenApply(ignored ->
                        queryToFutureContents.entrySet().stream()
                                .collect(toMap(
                                        Map.Entry::getKey,
                                        entry -> entry.getValue().join()
                                ))
                ).join();
    }

    private static void logQueries(Query originalQuery, Collection<Query> queries) {
        if (queries.size() == 1) {
            Query transformedQuery = queries.iterator().next();
            if (!transformedQuery.equals(originalQuery)) {
                log.debug("Transformed original query '{}' into '{}'",
                        originalQuery.text(), transformedQuery.text());
            }
        } else {
            log.debug("Transformed original query '{}' into the following queries:\n{}",
                    originalQuery.text(), queries.stream()
                            .map(Query::text)
                            .map(query -> "- '" + query + "'")
                            .collect(joining("\n")));
        }
    }

    private static void log(Query query, Collection<ContentRetriever> retrievers) {
        // TODO use retriever id
        if (retrievers.size() == 1) {
            log.debug("Routing query '{}' to the following retriever: {}",
                    query.text(), retrievers.iterator().next());
        } else {
            log.debug("Routing query '{}' to the following retrievers:\n{}",
                    query.text(), retrievers.stream()
                            .map(retriever -> "- " + retriever.toString())
                            .collect(joining("\n")));
        }
    }

    private static void log(Query query, ContentRetriever retriever, List<Content> contents) {
        // TODO use retriever id
        log.debug("Retrieved {} contents using query '{}' and retriever '{}'",
                contents.size(), query.text(), retriever);

        if (contents.size() > 0) {
            log.trace("Retrieved {} contents using query '{}' and retriever '{}':\n{}",
                    contents.size(), query.text(), retriever, contents.stream()
                            .map(Content::textSegment)
                            .map(segment -> "- " + escapeNewlines(segment.text()))
                            .collect(joining("\n")));
        }
    }

    private static void log(Map<Query, Collection<List<Content>>> queryToContents, List<Content> contents) {

        int contentCount = 0;
        for (Map.Entry<Query, Collection<List<Content>>> entry : queryToContents.entrySet()) {
            for (List<Content> contentList : entry.getValue()) {
                contentCount += contentList.size();
            }
        }
        if (contentCount == contents.size()) {
            return;
        }

        log.debug("Aggregated {} content(s) into {}", contentCount, contents.size());

        log.trace("Aggregated {} content(s) into:\n{}",
                contentCount, contents.stream()
                        .map(Content::textSegment)
                        .map(segment -> "- " + escapeNewlines(segment.text()))
                        .collect(joining("\n")));
    }

    private static void log(ChatMessage augmentedChatMessage) {
        log.trace("Augmented chat message: {}", escapeNewlines(augmentedChatMessage.text()));
    }

    private static String escapeNewlines(String text) {
        return text.replace("\n", "\\n");
    }

    public static DefaultRetrievalAugmentorBuilder builder() {
        return new DefaultRetrievalAugmentorBuilder();
    }

    public static class DefaultRetrievalAugmentorBuilder {

        public DefaultRetrievalAugmentorBuilder contentRetriever(ContentRetriever contentRetriever) {
            this.queryRouter = new DefaultQueryRouter(ensureNotNull(contentRetriever, "contentRetriever"));
            return this;
        }
    }
}
