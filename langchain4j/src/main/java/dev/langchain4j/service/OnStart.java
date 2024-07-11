package dev.langchain4j.service;

import dev.langchain4j.AbortController;

public interface OnStart {

    /**
     * Invoke this method to send a request to LLM and start response streaming.
     */
    default void start() {
        start(null);
    }

    void start(AbortController abortController);
}