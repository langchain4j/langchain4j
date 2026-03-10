package dev.langchain4j.store.embedding.listener;

import dev.langchain4j.Experimental;
import dev.langchain4j.store.embedding.EmbeddingStore;

/**
 * A {@link EmbeddingStore} listener that listens for requests, responses and errors.
 *
 * @since 1.11.0
 */
@Experimental
public interface EmbeddingStoreListener {

    /**
     * This method is called before the request is executed against the embedding store.
     *
     * @param requestContext The request context. It contains operation details and attributes.
     *                       The attributes can be used to pass data between methods of this listener
     *                       or between multiple listeners.
     */
    default void onRequest(EmbeddingStoreRequestContext<?> requestContext) {}

    /**
     * This method is called after a successful operation completes.
     *
     * @param responseContext The response context. It contains operation details and attributes.
     *                        The attributes can be used to pass data between methods of this listener
     *                        or between multiple listeners.
     */
    default void onResponse(EmbeddingStoreResponseContext<?> responseContext) {}

    /**
     * This method is called when an error occurs during interaction with the embedding store.
     *
     * @param errorContext The error context. It contains the error, operation details and attributes.
     *                     The attributes can be used to pass data between methods of this listener
     *                     or between multiple listeners.
     */
    default void onError(EmbeddingStoreErrorContext<?> errorContext) {}
}
