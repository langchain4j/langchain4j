package dev.langchain4j.model.openai.internal;

import java.util.function.Consumer;

public interface StreamingCompletionHandling {

    ErrorHandling onError(Consumer<Throwable> errorHandler);

    ErrorHandling ignoreErrors();
}
