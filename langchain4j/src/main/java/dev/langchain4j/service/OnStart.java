package dev.langchain4j.service;

public interface OnStart {

    /**
     * Invoke this method to send a request to LLM and start response streaming.
     */
    void start();
}