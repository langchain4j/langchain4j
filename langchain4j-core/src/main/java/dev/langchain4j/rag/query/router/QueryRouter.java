package dev.langchain4j.rag.query.router;

import dev.langchain4j.MightChangeInTheFuture;
import dev.langchain4j.classification.TextClassifier;
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
 * - Utilizing a Large Language Model (LLM) (see {@link LanguageModelQueryRouter})
 * - Employing a {@link TextClassifier} (aka "semantic routing")
 * </pre>
 *
 * @see DefaultQueryRouter
 * @see LanguageModelQueryRouter
 */
@MightChangeInTheFuture("This is an experimental feature. Time will tell if this is the right abstraction.")
public interface QueryRouter {

    /**
     * Routes the given {@link Query} to one or multiple {@link ContentRetriever}s.
     *
     * @param query The {@link Query} to be routed.
     * @return A collection of one or more {@link ContentRetriever}s to which the {@link Query} should be routed.
     */
    Collection<ContentRetriever> route(Query query);
}
