package dev.langchain4j.model.openai.internal;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Internal;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.ServerSentEvent;

@Internal
public class ParsedAndRawResponse<R> {

    private final R parsedResponse;
    private final SuccessfulHttpResponse rawResponse;
    private final ServerSentEvent rawEvent;

    ParsedAndRawResponse(R parsedResponse, SuccessfulHttpResponse rawResponse) {
        this.parsedResponse = ensureNotNull(parsedResponse, "parsedResponse");
        this.rawResponse = ensureNotNull(rawResponse, "rawResponse");
        this.rawEvent = null;
    }

    ParsedAndRawResponse(R parsedResponse, ServerSentEvent rawEvent) {
        this.parsedResponse = ensureNotNull(parsedResponse, "parsedResponse");
        this.rawResponse = null;
        this.rawEvent = ensureNotNull(rawEvent, "rawEvent");
    }

    public R parsedResponse() {
        return parsedResponse;
    }

    public SuccessfulHttpResponse rawResponse() {
        return rawResponse;
    }

    public ServerSentEvent rawEvent() {
        return rawEvent;
    }
}
