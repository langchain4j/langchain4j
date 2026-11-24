package dev.langchain4j.model.embedding.listener;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.embedding.EmbeddingModel;

/**
 * An {@link EmbeddingModel} listener that listens for requests, responses and errors.
 *
 * <p><b>Threading — these callbacks must not block.</b> They are always invoked synchronously, on the model's own
 * threads: on the synchronous API ({@code embed}) the caller's thread; on the asynchronous API ({@code embedAsync}),
 * {@link #onRequest} runs on the thread that starts the call and {@link #onResponse}/{@link #onError} on the
 * transport's I/O worker thread that reads the response (for HTTP models, the JDK HTTP client's {@code HttpClient-*}
 * workers). Blocking in a callback — a synchronous database write, a synchronous HTTP call to an observability
 * backend, synchronous file logging — stalls that worker and, under concurrency, degrades throughput for all
 * in-flight calls. Keep these methods non-blocking; if a callback must perform blocking I/O, offload it to your own
 * executor.
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
