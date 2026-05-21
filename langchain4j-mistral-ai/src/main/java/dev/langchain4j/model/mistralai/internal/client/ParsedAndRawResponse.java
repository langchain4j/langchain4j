package dev.langchain4j.model.mistralai.internal.client;

import dev.langchain4j.http.client.SuccessfulHttpResponse;

public class ParsedAndRawResponse<R> {

    private final R parsedResponse;
    private final SuccessfulHttpResponse rawResponse;

    public ParsedAndRawResponse(R parsedResponse, SuccessfulHttpResponse rawResponse) {
        this.parsedResponse = parsedResponse;
        this.rawResponse = rawResponse;
    }

    public R parsedResponse() {
        return parsedResponse;
    }

    public SuccessfulHttpResponse rawResponse() {
        return rawResponse;
    }
}
