package dev.langchain4j.model.decision.listener;

import dev.langchain4j.Experimental;

/**
 * A {@link dev.langchain4j.model.decision.DecisionModel} listener that listens for requests, responses and errors,
 * for example to log decisions for audit, or to record metrics and traces.
 * <p>
 * All methods are called on the thread that calls the model (or, for asynchronous calls, on the thread that completes
 * the call, which can be an I/O thread: do not block in listeners). Exceptions thrown by a listener are logged and
 * ignored.
 *
 * @since 1.21.0
 */
@Experimental
public interface DecisionModelListener {

    /**
     * Called before the request is sent to the model.
     *
     * @param requestContext the request and the attributes. The attributes can be used to pass data between the
     *                       methods of this listener, or between multiple listeners.
     */
    default void onRequest(DecisionModelRequestContext requestContext) {}

    /**
     * Called after the response is received from the model.
     *
     * @param responseContext the response, the corresponding request and the attributes.
     */
    default void onResponse(DecisionModelResponseContext responseContext) {}

    /**
     * Called when an error occurs during the interaction with the model.
     *
     * @param errorContext the error, the corresponding request and the attributes.
     */
    default void onError(DecisionModelErrorContext errorContext) {}
}
