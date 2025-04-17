package dev.langchain4j.http.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.internal.ValidationUtils.ensureBetween;
import static java.util.Collections.emptyMap;

public class SuccessfulHttpResponse {

    private final int statusCode;
    private final Map<String, List<String>> headers;
    private final String body;

    public SuccessfulHttpResponse(Builder builder) {
        this.statusCode = ensureBetween(builder.statusCode, 200, 299, "statusCode");
        this.headers = builder.headers == null ? emptyMap() : new HashMap<>(builder.headers);
        this.body = builder.body;
    }

    public int statusCode() {
        return statusCode;
    }

    public Map<String, List<String>> headers() {
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
        private Map<String, List<String>> headers;
        private String body;

        private Builder() {
        }

        public Builder statusCode(int statusCode) {
            this.statusCode = statusCode;
            return this;
        }

        public Builder headers(Map<String, List<String>> headers) {
            this.headers = headers;
            return this;
        }

        public Builder body(String body) {
            this.body = body;
            return this;
        }

        public SuccessfulHttpResponse build() {
            return new SuccessfulHttpResponse(this);
        }
    }
}
