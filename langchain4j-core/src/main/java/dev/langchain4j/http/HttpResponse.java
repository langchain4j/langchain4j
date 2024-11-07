package dev.langchain4j.http;

import dev.langchain4j.Experimental;
import java.util.HashMap;
import java.util.Map;

import static java.util.Collections.emptyMap;

@Experimental
public class HttpResponse {

    private final int statusCode;
    private final Map<String, String> headers;
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


    public static class HttpResponseBuilder {
        private int statusCode;
        private Map<String, String> headers;
        private String body;

        HttpResponseBuilder() {
        }

        /**
         * @return {@code this}.
         */
        public HttpResponse.HttpResponseBuilder statusCode(final int statusCode) {
            this.statusCode = statusCode;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public HttpResponse.HttpResponseBuilder headers(final Map<String, String> headers) {
            this.headers = headers;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public HttpResponse.HttpResponseBuilder body(final String body) {
            this.body = body;
            return this;
        }

        public HttpResponse build() {
            return new HttpResponse(this.statusCode, this.headers, this.body);
        }

        @java.lang.Override
        public java.lang.String toString() {
            return "HttpResponse.HttpResponseBuilder(statusCode=" + this.statusCode + ", headers=" + this.headers + ", body=" + this.body + ")";
        }
    }

    public static HttpResponse.HttpResponseBuilder builder() {
        return new HttpResponse.HttpResponseBuilder();
    }
}

