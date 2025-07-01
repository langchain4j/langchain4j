package dev.langchain4j.model.openai.internal;

import java.util.function.Consumer;

public interface SyncOrAsync<ResponseContent> {

    ResponseContent execute();

    default ParsedAndRawResponse<ResponseContent> executeRaw() {
        throw new UnsupportedOperationException("not implemented");
    }

    AsyncResponseHandling onResponse(Consumer<ResponseContent> responseHandler);
}
