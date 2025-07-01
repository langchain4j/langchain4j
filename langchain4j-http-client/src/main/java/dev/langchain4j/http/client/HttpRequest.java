package dev.langchain4j.http.client;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.util.Arrays.asList;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HttpRequest {

    private final HttpMethod method;
    private final String url;
    private final Map<String, List<String>> headers;
    private final String body;

    public HttpRequest(Builder builder) {
        this.method = ensureNotNull(builder.method, "method");
        this.url = ensureNotBlank(builder.url, "url");
        this.headers = copy(builder.headers);
        this.body = builder.body;
    }

    public HttpMethod method() {
        return method;
    }

    public String url() {
        return url;
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

        private HttpMethod method;
        private String url;
        private Map<String, List<String>> headers;
        private String body;

        private Builder() {}

        public Builder method(HttpMethod method) {
            this.method = method;
            return this;
        }

        public Builder url(String url) {
            this.url = url;
            return this;
        }

        public Builder url(String baseUrl, String path) {
            ensureNotBlank(baseUrl, "baseUrl");
            ensureNotBlank(path, "path");

            if (baseUrl.endsWith("/")) {
                baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            }
            if (path.startsWith("/")) {
                path = path.substring(1);
            }
            this.url = baseUrl + "/" + path;
            return this;
        }

        public Builder addHeader(String name, String... values) {
            ensureNotBlank(name, "name");
            ensureNotEmpty(values, "values");

            if (this.headers == null) {
                this.headers = new HashMap<>();
            }
            this.headers.put(name, asList(values));
            return this;
        }

        public Builder addHeaders(Map<String, String> headers) {
            if (isNullOrEmpty(headers)) {
                return this;
            }
            if (this.headers == null) {
                this.headers = new HashMap<>();
            }
            headers.forEach((name, value) -> this.headers.put(name, List.of(value)));
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

        public HttpRequest build() {
            return new HttpRequest(this);
        }
    }
}
