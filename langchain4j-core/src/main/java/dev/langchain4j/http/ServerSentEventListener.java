package dev.langchain4j.http;

import dev.langchain4j.Experimental;

@Experimental
public interface ServerSentEventListener {

    default void onStart(HttpResponse httpResponse) { // TODO needed?

    }

    void onEvent(ServerSentEvent event);

    void onError(Throwable throwable);

    default void onFinish() { // TODO needed?

    }
}
