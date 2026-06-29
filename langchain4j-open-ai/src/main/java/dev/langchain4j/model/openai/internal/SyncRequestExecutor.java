package dev.langchain4j.model.openai.internal;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import java.util.function.Function;

class SyncRequestExecutor<Response> {

    private final HttpClient httpClient;
    private final HttpRequest httpRequest;
    private final Class<Response> responseClass;
    private final Function<SuccessfulHttpResponse, Response> responseMapper;

    SyncRequestExecutor(HttpClient httpClient, HttpRequest httpRequest, Class<Response> responseClass) {
        this(httpClient, httpRequest, responseClass, null);
    }

    SyncRequestExecutor(
            HttpClient httpClient,
            HttpRequest httpRequest,
            Class<Response> responseClass,
            Function<SuccessfulHttpResponse, Response> responseMapper) {
        this.httpClient = httpClient;
        this.httpRequest = httpRequest;
        this.responseClass = responseClass;
        this.responseMapper = responseMapper;
    }

    ParsedAndRawResponse<Response> execute() {
        SuccessfulHttpResponse rawHttpResponse = httpClient.execute(httpRequest);
        Response parsedResponse = responseMapper != null
                ? responseMapper.apply(rawHttpResponse)
                : Json.fromJson(rawHttpResponse.body(), responseClass);
        return new ParsedAndRawResponse<>(parsedResponse, rawHttpResponse);
    }
}
