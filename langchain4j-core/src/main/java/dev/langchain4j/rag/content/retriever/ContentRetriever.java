package dev.langchain4j.rag.content.retriever;

import static dev.langchain4j.internal.Utils.isNullOrEmpty;

import dev.langchain4j.Experimental;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.listener.ContentRetrieverListener;
import dev.langchain4j.rag.query.Query;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Retrieves {@link Content}s from an underlying data source using a given {@link Query}.
 * <br>
 * The goal is to retrieve only relevant {@link Content}s in relation to a given {@link Query}.
 * <br>
 * The underlying data source can be virtually anything:
 * <pre>
 * - Embedding (vector) store (see {@link EmbeddingStoreContentRetriever})
 * - Full-text search engine (see {@code AzureAiSearchContentRetriever} in the {@code langchain4j-azure-ai-search} module)
 * - Hybrid of vector and full-text search (see {@code AzureAiSearchContentRetriever} in the {@code langchain4j-azure-ai-search} module)
 * - Web Search Engine (see {@link WebSearchContentRetriever})
 * - Knowledge graph (see {@code Neo4jContentRetriever} in the {@code langchain4j-community-neo4j-retriever} module)
 * - SQL database (see {@code SqlDatabaseContentRetriever} in the {@code langchain4j-experimental-sql} module)
 * - etc.
 * </pre>
 *
 * @see EmbeddingStoreContentRetriever
 * @see WebSearchContentRetriever
 */
public interface ContentRetriever {

    /**
     * Retrieves relevant {@link Content}s using a given {@link Query}.
     * The {@link Content}s are sorted by relevance, with the most relevant {@link Content}s appearing
     * at the beginning of the returned {@code List<Content>}.
     *
     * @param query The {@link Query} to use for retrieval.
     * @return A list of retrieved {@link Content}s.
     */
    List<Content> retrieve(Query query);

    /**
     * Non-blocking counterpart of {@link #retrieve(Query)}, invoked by the asynchronous
     * ({@link java.util.concurrent.CompletableFuture}/{@link java.util.concurrent.CompletionStage}) and reactive
     * ({@link java.util.concurrent.Flow.Publisher}) AI Service modes when RAG is configured.
     * <p>
     * The default implementation throws {@link UnsupportedOperationException}: a retriever backed by blocking I/O
     * (embedding-model call, vector-store query, web search, etc.) must opt in by overriding this method so it can
     * return a genuinely non-blocking future instead of secretly tying up a worker thread. A retriever that cannot
     * be made non-blocking is still usable from these modes via {@code DefaultRetrievalAugmentor}, which offloads the
     * blocking {@link #retrieve(Query)} to its executor.
     * <p>
     * An implementation that honors cancellation should abort its in-flight I/O when the returned future is
     * cancelled (best-effort); Java cannot safely interrupt arbitrary code, so this is not guaranteed.
     *
     * @param query The {@link Query} to use for retrieval.
     * @return A {@link CompletableFuture} of the retrieved {@link Content}s, sorted by relevance.
     * @since 1.18.0
     */
    default CompletableFuture<List<Content>> retrieveAsync(Query query) {
        throw new UnsupportedOperationException("retrieveAsync() is not implemented by " + getClass().getName());
    }

    /**
     * Wraps this {@link ContentRetriever} with a listening retriever that dispatches events to the provided listener.
     *
     * @param listener The listener to add.
     * @return An observing {@link ContentRetriever} that will dispatch events to the provided listener.
     * @since 1.11.0
     */
    @Experimental
    default ContentRetriever addListener(ContentRetrieverListener listener) {
        return addListeners(listener == null ? null : List.of(listener));
    }

    /**
     * Wraps this {@link ContentRetriever} with a listening retriever that dispatches events to the provided listeners.
     * <p>
     * Listeners are called in the order of iteration.
     *
     * @param listeners The listeners to add.
     * @return An observing {@link ContentRetriever} that will dispatch events to the provided listeners.
     * @since 1.11.0
     */
    @Experimental
    default ContentRetriever addListeners(Collection<ContentRetrieverListener> listeners) {
        if (isNullOrEmpty(listeners)) {
            return this;
        }
        if (this instanceof ListeningContentRetriever listeningContentRetriever) {
            return listeningContentRetriever.withAdditionalListeners(listeners);
        }
        if (listeners instanceof List<ContentRetrieverListener> listenersList) {
            return new ListeningContentRetriever(this, listenersList);
        } else {
            return new ListeningContentRetriever(this, new ArrayList<>(listeners));
        }
    }
}
