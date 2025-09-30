package dev.langchain4j.model.openai.internal;

import dev.langchain4j.Internal;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.ServerSentEvent;

@Internal
public class ParsedAndRawResponse<R> {

    private final R parsedResponse;
    private final SuccessfulHttpResponse rawHttpResponse;
    private final ServerSentEvent rawServerSentEvent;

    protected ParsedAndRawResponse(R parsedResponse, SuccessfulHttpResponse rawHttpResponse) {
        this.parsedResponse = parsedResponse;
        this.rawHttpResponse = rawHttpResponse;
        this.rawServerSentEvent = null;
    }

    protected ParsedAndRawResponse(R parsedResponse, ServerSentEvent rawServerSentEvent) {
        this.parsedResponse = parsedResponse;
        this.rawHttpResponse = null;
        this.rawServerSentEvent = rawServerSentEvent;
    }

    public R parsedResponse() {
        return parsedResponse;
    }

    public SuccessfulHttpResponse rawHttpResponse() {
        return rawHttpResponse;
    }

    public ServerSentEvent rawServerSentEvent() {
        return rawServerSentEvent;
    }
}
