package dev.langchain4j.rag.content.retriever.listener;

import dev.langchain4j.Experimental;
import dev.langchain4j.rag.content.retriever.ContentRetriever;

/**
 * A {@link ContentRetriever} listener that listens for requests, responses and errors.
 *
 * @since 1.11.0
 */
@Experimental
public interface ContentRetrieverListener {

    /**
     * This method is called before the request is executed against the retriever.
     *
     * @param requestContext The request context. It contains the {@link dev.langchain4j.rag.query.Query} and attributes.
     *                       The attributes can be used to pass data between methods of this listener
     *                       or between multiple listeners.
     */
    default void onRequest(ContentRetrieverRequestContext requestContext) {}

    /**
     * This method is called after a successful retrieval.
     *
     * @param responseContext The response context. It contains retrieved content, corresponding query and attributes.
     *                        The attributes can be used to pass data between methods of this listener
     *                        or between multiple listeners.
     */
    default void onResponse(ContentRetrieverResponseContext responseContext) {}

    /**
     * This method is called when an error occurs during retrieval.
     *
     * @param errorContext The error context. It contains the error, corresponding query and attributes.
     *                     The attributes can be used to pass data between methods of this listener
     *                     or between multiple listeners.
     */
    default void onError(ContentRetrieverErrorContext errorContext) {}
}
