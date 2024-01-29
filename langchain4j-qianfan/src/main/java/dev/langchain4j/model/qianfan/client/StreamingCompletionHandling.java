package dev.langchain4j.model.qianfan.client;

import java.util.function.Consumer;

public interface StreamingCompletionHandling {

    ErrorHandling onError(Consumer<Throwable> var1);

    ErrorHandling ignoreErrors();
}
