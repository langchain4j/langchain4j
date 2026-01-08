package dev.langchain4j.agentic.internal;

public interface DelayedResponse<T> {

    boolean isDone();

    T blockingGet();

    default Object result() {
        return isDone() ? blockingGet() : "<pending>";
    }
}
