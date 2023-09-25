package dev.langchain4j.service;

import java.util.function.Consumer;

/**
 * Represents a token stream from language model to which you can subscribe and receive updates
 * when a new token is available, when language model finishes streaming, or when an error occurs during streaming.
 * It is intended to be used as a return type in AI Service.
 */
public interface TokenStream {

    /**
     * The provided consumer will be invoked every time a new token from a language model is available.
     *
     * @param tokenHandler lambda that consumes tokens of the response
     * @return the next step of a step-builder
     */
    OnCompleteOrOnError onNext(Consumer<String> tokenHandler);
}