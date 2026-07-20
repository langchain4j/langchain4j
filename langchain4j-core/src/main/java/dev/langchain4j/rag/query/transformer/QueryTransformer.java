package dev.langchain4j.rag.query.transformer;

import dev.langchain4j.exception.AsyncNotSupportedException;
import dev.langchain4j.rag.query.Query;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

/**
 * Transforms the given {@link Query} into one or multiple {@link Query}s.
 * <br>
 * The goal is to enhance retrieval quality by modifying or expanding the original {@link Query}.
 * <br>
 * Some known approaches to improve retrieval include:
 * <pre>
 * - Query compression (see {@link CompressingQueryTransformer})
 * - Query expansion (see {@link ExpandingQueryTransformer})
 * - Query re-writing
 * - Step-back prompting
 * - Hypothetical document embeddings (HyDE)
 * </pre>
 * Additional details can be found <a href="https://blog.langchain.dev/query-transformations/">here</a>.
 *
 * @see DefaultQueryTransformer
 * @see CompressingQueryTransformer
 * @see ExpandingQueryTransformer
 */
public interface QueryTransformer {

    /**
     * Transforms the given {@link Query} into one or multiple {@link Query}s.
     *
     * @param query The {@link Query} to be transformed.
     * @return A collection of one or more {@link Query}s derived from the original {@link Query}.
     */
    Collection<Query> transform(Query query);

    /**
     * Non-blocking counterpart of {@link #transform(Query)}, invoked by the asynchronous
     * ({@link java.util.concurrent.CompletableFuture}/{@link java.util.concurrent.CompletionStage}) and reactive
     * ({@link java.util.concurrent.Flow.Publisher}) AI Service modes when RAG is configured.
     * <p>
     * The default implementation throws {@link UnsupportedOperationException}: a transformer backed by a blocking
     * LLM call (e.g. query compression/expansion) must opt in by overriding this method to stay off the calling
     * thread. A transformer that cannot be made non-blocking is still usable from these modes via
     * {@code DefaultRetrievalAugmentor}, which offloads the blocking {@link #transform(Query)} to its executor.
     *
     * @param query The {@link Query} to be transformed.
     * @return A {@link CompletableFuture} of one or more {@link Query}s derived from the original {@link Query}.
     * @since 1.19.0
     */
    default CompletableFuture<Collection<Query>> transformAsync(Query query) {
        throw new AsyncNotSupportedException("transformAsync() is not implemented by " + getClass().getName());
    }
}

