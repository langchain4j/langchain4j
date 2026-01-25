package dev.langchain4j.model.embedding.listener;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.embedding.EmbeddingModel;

/**
 * An {@link EmbeddingModel} listener that listens for requests, responses and errors.
 *
 * @since 1.11.0
 */
@Experimental
public interface EmbeddingModelListener {

    /**
     * This method is called before the request is executed against the embedding model.
     *
     * @param requestContext The request context. It contains the input and attributes.
     *                       The attributes can be used to pass data between methods of this listener
     *                       or between multiple listeners.
     */
    default void onRequest(EmbeddingModelRequestContext requestContext) {}

    /**
     * This method is called after a successful embedding operation completes.
     *
     * @param responseContext The response context. It contains the response, corresponding request and attributes.
     *                        The attributes can be used to pass data between methods of this listener
     *                        or between multiple listeners.
     */
    default void onResponse(EmbeddingModelResponseContext responseContext) {}

    /**
     * This method is called when an error occurs during interaction with the embedding model.
     *
     * @param errorContext The error context. It contains the error, corresponding request and attributes.
     *                     The attributes can be used to pass data between methods of this listener
     *                     or between multiple listeners.
     */
    default void onError(EmbeddingModelErrorContext errorContext) {}
}
