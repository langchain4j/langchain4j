package dev.langchain4j.service;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.output.Response;

import java.util.function.Consumer;

public interface OnCompleteOrOnError {

    /**
     * The provided consumer will be invoked when a language model finishes streaming a response.
     *
     * @param completionHandler lambda that will be invoked when language model finishes streaming
     * @return the next step of the step-builder
     */
    OnError onComplete(Consumer<Response<AiMessage>> completionHandler);

    /**
     * The provided consumer will be invoked when an error occurs during streaming.
     *
     * @param errorHandler lambda that will be invoked when an error occurs
     * @return the next step of the step-builder
     */
    OnStart onError(Consumer<Throwable> errorHandler);

    /**
     * All errors during streaming will be ignored (but will be logged with a WARN log level).
     *
     * @return the next step of the step-builder
     */
    OnStart ignoreErrors();
}