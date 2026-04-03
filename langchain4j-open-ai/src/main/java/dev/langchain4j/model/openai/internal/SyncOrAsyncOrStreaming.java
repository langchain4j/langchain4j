package dev.langchain4j.model.openai.internal;

import java.util.function.Consumer;
import dev.langchain4j.http.client.sse.ServerSentEvent;

public interface SyncOrAsyncOrStreaming<ResponseContent> extends SyncOrAsync<ResponseContent> {

    StreamingResponseHandling onPartialResponse(Consumer<ResponseContent> partialResponseHandler);

    default StreamingResponseHandling onRawPartialResponse(Consumer<ParsedAndRawResponse<ResponseContent>> handler) {
        ServerSentEvent rawEvent = null;
        return onPartialResponse(parsedResponse -> handler.accept(new ParsedAndRawResponse<>(parsedResponse, rawEvent)));
    }
}
