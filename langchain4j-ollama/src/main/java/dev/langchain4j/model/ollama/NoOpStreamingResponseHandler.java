package dev.langchain4j.model.ollama;

import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.StreamingResponseHandler;

/**
 * A no-operation (no-op) implementation of StreamingResponseHandler that performs no actions.
 *
 * @param <T> The type of the response.
 */
public class NoOpStreamingResponseHandler<T> implements StreamingResponseHandler<T> {

    /**
     * Does nothing when a new token is generated.
     *
     * @param token The newly generated token, which is a part of the complete response.
     */
    @Override
    public void onNext(String token) {
        // No operation
    }

    /**
     * Does nothing when the language model has finished streaming a response.
     *
     * @param response The complete response generated by the language model.
     */
    @Override
    public void onComplete(Response<T> response) {
        // No operation
    }

    /**
     * Does nothing when an error occurs during streaming.
     *
     * @param error The error that occurred
     */
    @Override
    public void onError(Throwable error) {
        // No operation
    }
}