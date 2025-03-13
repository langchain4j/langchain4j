package dev.langchain4j.model.openai.internal;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;

import java.util.Map;

import static dev.langchain4j.model.openai.internal.ResponseAndAttributes.RAW_RESPONSE_ATTRIBUTE;

class SyncRequestExecutor<Response> {

    private final HttpClient httpClient;
    private final HttpRequest httpRequest;
    private final Class<Response> responseClass;

    SyncRequestExecutor(HttpClient httpClient, HttpRequest httpRequest, Class<Response> responseClass) {
        this.httpClient = httpClient;
        this.httpRequest = httpRequest;
        this.responseClass = responseClass;
    }

    ResponseAndAttributes<Response> execute() {
        SuccessfulHttpResponse successfulHttpResponse = httpClient.execute(httpRequest);
        Response response = Json.fromJson(successfulHttpResponse.body(), responseClass);
        Map<String, Object> attributes = Map.of(RAW_RESPONSE_ATTRIBUTE, successfulHttpResponse);
        return new ResponseAndAttributes<>(response, attributes);
    }
}
