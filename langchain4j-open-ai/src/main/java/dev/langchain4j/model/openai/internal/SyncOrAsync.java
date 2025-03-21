package dev.langchain4j.model.openai.internal;

import java.util.function.Consumer;

public interface SyncOrAsync<Response> {

    ResponseAndAttributes<Response> execute();

    AsyncResponseHandling onResponse(Consumer<ResponseAndAttributes<Response>> responseHandler);
}
