package dev.langchain4j.model;

import dev.langchain4j.WillChangeSoon;

@WillChangeSoon("Most probably will be replaced with fluent API")
public interface StreamingResultHandler {

    void onPartialResult(String partialResult);

    default void onComplete() {
    }

    void onError(Throwable error);
}
