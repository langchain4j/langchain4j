package dev.langchain4j.http.client.sse;

import dev.langchain4j.Experimental;
import dev.langchain4j.http.client.SuccessfulHttpResponse;

public interface ServerSentEventListener {

    default void onOpen(SuccessfulHttpResponse response) {}

    /**
     * Handles server-sent event.
     *
     * @since 1.8.0
     */
    @Experimental
    default void onEvent(ServerSentEvent event, ServerSentEventContext context) {
        onEvent(event);
    }

    /**
     * NOTE: This is an outdated method. If you want to use stream cancellation feature,
     * implement and use {@link #onEvent(ServerSentEvent, ServerSentEventContext)} instead.
     * <br>
     * Handles server-sent event.
     *
     * @see #onEvent(ServerSentEvent, ServerSentEventContext)
     */
    default void onEvent(ServerSentEvent event) {}

    void onError(Throwable throwable);

    default void onClose() {}
}
