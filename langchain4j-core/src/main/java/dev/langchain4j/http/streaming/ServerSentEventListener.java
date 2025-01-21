package dev.langchain4j.http.streaming;

import dev.langchain4j.Experimental;
import dev.langchain4j.http.SuccessfulHttpResponse;

@Experimental
public interface ServerSentEventListener {

    default void onOpen(SuccessfulHttpResponse response) {

    }

    void onEvent(ServerSentEvent event);

    void onError(Throwable throwable);

    default void onClose() {

    }
}
