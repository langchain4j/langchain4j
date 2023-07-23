package dev.langchain4j.service;

import java.util.function.Consumer;

public interface OnError {

    /**
     * The provided Consumer will be invoked when an error occurs during streaming.
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