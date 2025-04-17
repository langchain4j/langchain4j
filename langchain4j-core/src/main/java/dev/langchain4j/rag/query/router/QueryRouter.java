package dev.langchain4j.rag.query.router;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;

import java.util.Collection;

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
}
