package dev.langchain4j.model.openai.internal;

import java.util.function.Consumer;

public interface SyncOrAsync<ResponseContent> {

    ResponseContent execute();

    default ResponseAndAttributes<ResponseContent> executeRaw() { // TODO name
        throw new UnsupportedOperationException("not implemented");
    };

    AsyncResponseHandling onResponse(Consumer<ResponseContent> responseHandler);
}
