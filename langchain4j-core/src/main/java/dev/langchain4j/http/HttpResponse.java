package dev.langchain4j.http;

import dev.langchain4j.Experimental;
import lombok.Builder;

import java.util.HashMap;
import java.util.Map;

import static java.util.Collections.emptyMap;

@Experimental
public class HttpResponse {

    private final int statusCode;
    private final Map<String, String> headers;
    private final String body;

    @Builder
    public HttpResponse(int statusCode, Map<String, String> headers, String body) {
        this.statusCode = statusCode;
        this.headers = headers == null ? emptyMap() : new HashMap<>(headers);
        this.body = body;
    }

    public int statusCode() {
        return statusCode;
    }

    public Map<String, String> headers() {
        return headers;
    }

    public String body() {
        return body;
    }
}
