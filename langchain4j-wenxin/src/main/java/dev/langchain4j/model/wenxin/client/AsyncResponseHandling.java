package dev.langchain4j.model.wenxin.client;

import java.util.function.Consumer;

public interface AsyncResponseHandling {
    ErrorHandling onError(Consumer<Throwable> var1);

    ErrorHandling ignoreErrors();
}