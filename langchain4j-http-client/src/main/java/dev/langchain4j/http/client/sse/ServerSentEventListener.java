package dev.langchain4j.http.client.sse;

import dev.langchain4j.Experimental;
import dev.langchain4j.http.client.SuccessfulHttpResponse;

/**
 * TODO
 */
@Experimental
public interface ServerSentEventListener {

    /**
     * TODO
     *
     * @param response
     */
    default void onOpen(SuccessfulHttpResponse response) {

    }

    /**
     * TODO
     *
     * @param event
     */
    void onEvent(ServerSentEvent event);

    /**
     * TODO
     *
     * @param throwable
     */
    void onError(Throwable throwable);

    /**
     * TODO
     */
    default void onClose() {

    }
}
