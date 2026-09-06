package dev.langchain4j.model.chat.listener;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

/**
 * A {@link ChatModel} listener that listens for requests, responses and errors.
 *
 * <p><b>Threading — these callbacks must not block.</b> They are always invoked synchronously, on the model's own
 * threads: on the synchronous API ({@code chat}) the caller's thread; on the asynchronous and reactive APIs
 * ({@code chatAsync} and the streaming {@code chat}), {@link #onRequest} runs on the thread that starts the call and
 * {@link #onResponse}/{@link #onError} on the transport's I/O worker thread that reads the response (for HTTP models,
 * the JDK HTTP client's {@code HttpClient-*} workers). Blocking in a callback — a synchronous database write, a
 * synchronous HTTP call to an observability backend, synchronous file logging — stalls that worker and, under
 * concurrency, degrades throughput for all in-flight calls, exactly as blocking in a reactive {@code onNext} would.
 * Keep these methods non-blocking: record metrics or start/stop tracing spans (as the built-in Micrometer and
 * observation listeners do); if a callback must perform blocking I/O, offload it to your own executor.
 */
public interface ChatModelListener {

    /**
     * This method is called before the request is sent to the model.
     *
     * @param requestContext The request context. It contains the {@link ChatRequest} and attributes.
     *                       The attributes can be used to pass data between methods of this listener
     *                       or between multiple listeners.
     */
    default void onRequest(ChatModelRequestContext requestContext) {

    }

    /**
     * This method is called after the response is received from the model.
     *
     * @param responseContext The response context.
     *                        It contains {@link ChatResponse}, corresponding {@link ChatRequest} and attributes.
     *                        The attributes can be used to pass data between methods of this listener
     *                        or between multiple listeners.
     */
    default void onResponse(ChatModelResponseContext responseContext) {

    }

    /**
     * This method is called when an error occurs during interaction with the model.
     *
     * @param errorContext The error context.
     *                     It contains the error, corresponding {@link ChatRequest},
     *                     partial {@link ChatResponse} (if available) and attributes.
     *                     The attributes can be used to pass data between methods of this listener
     *                     or between multiple listeners.
     */
    default void onError(ChatModelErrorContext errorContext) {

    }
}
