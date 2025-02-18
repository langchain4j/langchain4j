package dev.langchain4j.model.openai.internal;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;

class SyncRequestExecutor<Response> {

    private final HttpClient httpClient;
    private final HttpRequest httpRequest;
    private final Class<Response> responseClass;

    SyncRequestExecutor(HttpClient httpClient, HttpRequest httpRequest, Class<Response> responseClass) {
        this.httpClient = httpClient;
        this.httpRequest = httpRequest;
        this.responseClass = responseClass;
    }

    Response execute() {
        SuccessfulHttpResponse successfulHttpResponse = httpClient.execute(httpRequest);
        return Json.fromJson(successfulHttpResponse.body(), responseClass);
    }
}
