package dev.langchain4j.model.openai.internal;

import java.util.function.Consumer;

public interface SyncOrAsyncOrStreaming<Response> extends SyncOrAsync<Response> {

    StreamingResponseHandling onPartialResponse(Consumer<ResponseAndAttributes<Response>> partialResponseHandler);
}
