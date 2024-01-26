package dev.langchain4j.rag.query.transformer;

import dev.langchain4j.MightChangeInTheFuture;
import dev.langchain4j.rag.query.Query;

import java.util.Collection;

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
@MightChangeInTheFuture("This is an experimental feature. Time will tell if this is the right abstraction.")
public interface QueryTransformer {

    /**
     * Transforms the given {@link Query} into one or multiple {@link Query}s.
     *
     * @param query The {@link Query} to be transformed.
     * @return A collection of one or more {@link Query}s derived from the original {@link Query}.
     */
    Collection<Query> transform(Query query);
}

