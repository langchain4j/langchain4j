package dev.langchain4j.http.client.sse;

import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.model.chat.response.StreamingHandle;

public interface ServerSentEventListener {

    default void onOpen(SuccessfulHttpResponse response) {}

    void onEvent(ServerSentEvent event);

    default void onEvent(ServerSentEvent event, StreamingHandle streamingHandle) { // TODO use other (more low-level) type? accept single object?
        onEvent(event); // TODO?
    }

    void onError(Throwable throwable);

    default void onClose() {}
}
