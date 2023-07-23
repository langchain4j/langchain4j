package dev.langchain4j.service;

import java.util.function.Consumer;

/**
 * Represents a token stream from LLM to which you can subscribe and receive updates
 * when a new token of response is available, when LLM finishes streaming, or when an error occurs during streaming.
 * It is intended to be used as a return type in AI Service.
 */
public interface TokenStream {

    /**
     * The provided Consumer will be invoked every time a new response token from LLM is available.
     *
     * @param tokenHandler lambda that consumes response tokens
     * @return the next step of a step-builder
     */
    OnCompleteOrOnError onNext(Consumer<String> tokenHandler);
}