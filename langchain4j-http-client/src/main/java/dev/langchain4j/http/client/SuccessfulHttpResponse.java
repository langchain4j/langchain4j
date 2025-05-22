package dev.langchain4j.http.client;

import java.net.http.HttpClient;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.ValidationUtils.ensureBetween;

public class SuccessfulHttpResponse {

    private final int statusCode;
    private final Map<String, List<String>> headers;
    private final String body;
    private final HttpClient.Version version;

    public SuccessfulHttpResponse(Builder builder) {
        this.statusCode = ensureBetween(builder.statusCode, 200, 299, "statusCode");
        this.headers = copy(builder.headers);
        this.body = builder.body;
        this.version = builder.version;
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

    public HttpClient.Version version() {
        return version;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private int statusCode;
        private Map<String, List<String>> headers;
        private String body;
        private HttpClient.Version version;

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

        public Builder version(HttpClient.Version version) {
            this.version = version;
            return this;
        }


        public SuccessfulHttpResponse build() {
            return new SuccessfulHttpResponse(this);
        }
    }
}
