package dev.langchain4j.rag.query.router;

import dev.langchain4j.exception.AsyncNotSupportedException;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

/**
 * Routes the given {@link Query} to one or multiple {@link ContentRetriever}s.
 * <br>
 * The goal is to ensure that {@link Content} is retrieved only from relevant data sources.
 * <br>
 * Some potential approaches include:
 * <pre>
 * - Using an LLM (see {@link LanguageModelQueryRouter})
 * - Using an {@link EmbeddingModel} (aka "semantic routing", see {@code EmbeddingModelTextClassifier} in the {@code langchain4j} module)
 * - Using keyword-based routing
 * - Route depending on the user ({@code query.metadata().chatMemoryId()}) and/or permissions
 * </pre>
 *
 * @see DefaultQueryRouter
 * @see LanguageModelQueryRouter
 */
public interface QueryRouter {

    /**
     * Routes the given {@link Query} to one or multiple {@link ContentRetriever}s.
     *
     * @param query The {@link Query} to be routed.
     * @return A collection of one or more {@link ContentRetriever}s to which the {@link Query} should be routed.
     */
    Collection<ContentRetriever> route(Query query);

    /**
     * Non-blocking counterpart of {@link #route(Query)}, invoked by the asynchronous
     * ({@link java.util.concurrent.CompletableFuture}/{@link java.util.concurrent.CompletionStage}) and reactive
     * ({@link java.util.concurrent.Flow.Publisher}) AI Service modes when RAG is configured.
     * <p>
     * The default implementation throws {@link UnsupportedOperationException}: a router backed by a blocking LLM
     * call (e.g. {@link LanguageModelQueryRouter}) must opt in by overriding this method to stay off the calling
     * thread. A router that cannot be made non-blocking is still usable from these modes via
     * {@code DefaultRetrievalAugmentor}, which offloads the blocking {@link #route(Query)} to its executor.
     *
     * @param query The {@link Query} to be routed.
     * @return A {@link CompletableFuture} of one or more {@link ContentRetriever}s to route the {@link Query} to.
     * @since 1.19.0
     */
    default CompletableFuture<Collection<ContentRetriever>> routeAsync(Query query) {
        throw new AsyncNotSupportedException("routeAsync() is not implemented by " + getClass().getName());
    }
}
