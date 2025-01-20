package dev.langchain4j.http;

import dev.langchain4j.Experimental;

import java.util.HashMap;
import java.util.Map;

import static java.util.Collections.emptyMap;

@Experimental
public class HttpResponse {

    private final int statusCode;
    private final Map<String, String> headers; // TODO Map<String, List<String>>
    private final String body;

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

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private int statusCode;
        private Map<String, String> headers;
        private String body;

        private Builder() {
        }

        public Builder statusCode(int statusCode) {
            this.statusCode = statusCode;
            return this;
        }

        public Builder headers(Map<String, String> headers) {
            this.headers = headers;
            return this;
        }

        public Builder body(String body) {
            this.body = body;
            return this;
        }

        public HttpResponse build() {
            return new HttpResponse(statusCode, headers, body);
        }
    }
}
