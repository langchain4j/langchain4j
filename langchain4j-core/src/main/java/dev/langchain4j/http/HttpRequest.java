package dev.langchain4j.http;

import dev.langchain4j.Experimental;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.util.Collections.emptyMap;

@Experimental
public class HttpRequest {

    private final HttpMethod method;
    private final String url;
    private final Map<String, String> headers; // TODO Map<String, List<String>>
    private final String body; // TODO type

    public HttpRequest(Builder builder) {
        this.method = ensureNotNull(builder.method, "method");
        this.url = ensureNotBlank(builder.url, "url");
        this.headers = builder.headers == null ? emptyMap() : new LinkedHashMap<>(builder.headers);
        this.body = builder.body;
    }

    public HttpMethod method() {
        return method;
    }

    public String url() {
        return url;
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

        private HttpMethod method;
        private String url;
        private Map<String, String> headers;
        private String body;

        private Builder() {
        }

        public Builder method(HttpMethod method) {
            this.method = method;
            return this;
        }

        public Builder url(String url) {
            this.url = url;
            return this;
        }

        // TODO unit test
        public Builder url(String baseUrl, String path) {
            if (baseUrl.endsWith("/")) {
                baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            }
            if (path.startsWith("/")) {
                path = path.substring(1);
            }
            this.url = baseUrl + "/" + path;
            return this;
        }

        // TODO unit test
        public Builder addHeader(String name, String value) {
            if (this.headers == null) {
                this.headers = new HashMap<>();
            }
            this.headers.put(ensureNotBlank(name, "name"), value);
            return this;
        }

        // TODO unit test
        public Builder addHeaders(Map<String, String> headers) {
            if (isNullOrEmpty(headers)) {
                return this;
            }
            if (this.headers == null) {
                this.headers = new HashMap<>();
            }
            this.headers.putAll(headers);
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

        public HttpRequest build() {
            return new HttpRequest(this);
        }
    }
}
