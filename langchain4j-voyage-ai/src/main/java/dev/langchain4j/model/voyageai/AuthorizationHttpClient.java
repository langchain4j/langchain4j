package dev.langchain4j.model.voyageai;

import static java.util.Collections.singletonList;

import dev.langchain4j.exception.HttpException;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;
import java.util.List;
import java.util.Map;

class AuthorizationHttpClient implements HttpClient {

    private final HttpClient delegateHttpClient;
    private final String apiKey;

    AuthorizationHttpClient(HttpClient delegateHttpClient, String apiKey) {
        this.delegateHttpClient = delegateHttpClient;
        this.apiKey = apiKey;
    }

    @Override
    public SuccessfulHttpResponse execute(HttpRequest request) throws HttpException, RuntimeException {
        addAuthorizationHeader(request.headers());

        return delegateHttpClient.execute(request);
    }

    @Override
    public void execute(HttpRequest request, ServerSentEventParser parser, ServerSentEventListener listener) {
        addAuthorizationHeader(request.headers());

        delegateHttpClient.execute(request, parser, listener);
    }

    private void addAuthorizationHeader(Map<String, List<String>> headers) {
        headers.put("Authorization", singletonList("Bearer " + apiKey));
    }
}
