package dev.langchain4j.model.wenxin.client;

import java.util.function.Consumer;

public interface StreamingCompletionHandling {

    ErrorHandling onError(Consumer<Throwable> var1);

    ErrorHandling ignoreErrors();
}
