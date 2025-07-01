package dev.langchain4j.http.client.sse;

import dev.langchain4j.http.client.SuccessfulHttpResponse;

public interface ServerSentEventListener {

    default void onOpen(SuccessfulHttpResponse response) {}

    void onEvent(ServerSentEvent event);

    void onError(Throwable throwable);

    default void onClose() {}
}
