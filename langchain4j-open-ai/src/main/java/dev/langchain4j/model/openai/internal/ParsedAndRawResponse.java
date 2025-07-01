package dev.langchain4j.model.openai.internal;

import dev.langchain4j.Internal;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.ServerSentEvent;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

@Internal
public class ParsedAndRawResponse<Response> { // TODO public? name

    public static final String RAW_RESPONSE_ATTRIBUTE = "rawResponse";

    private final Response response;
    private final SuccessfulHttpResponse rawResponse;
    private final ServerSentEvent rawEvent;

    public ParsedAndRawResponse(Response response, SuccessfulHttpResponse rawResponse) {
        this.response = ensureNotNull(response, "response");
        this.rawResponse = ensureNotNull(rawResponse, "rawResponse");
        this.rawEvent = null;
    }

    public ParsedAndRawResponse(Response response, ServerSentEvent rawEvent) {
        this.response = ensureNotNull(response, "response");
        this.rawResponse = null;
        this.rawEvent = ensureNotNull(rawEvent, "rawEvent");
    }

    public Response response() {
        return response;
    }

    public SuccessfulHttpResponse rawResponse() {
        return rawResponse;
    }

    public ServerSentEvent rawEvent() {
        return rawEvent;
    }
}
