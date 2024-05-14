package dev.langchain4j.http;

import dev.langchain4j.Experimental;
import lombok.Builder;

import java.util.HashMap;
import java.util.Map;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.util.Collections.emptyMap;

@Experimental
public class HttpRequest {

    private final HttpMethod method;
    private final String url;
    private final Map<String, String> headers;
    private final String body;

    @Builder
    public HttpRequest(HttpMethod method, String url, Map<String, String> headers, String body) {
        this.method = ensureNotNull(method, "method");
        this.url = ensureNotBlank(url, "url");
        this.headers = headers == null ? emptyMap() : new HashMap<>(headers);
        this.body = ensureNotNull(body, "body"); // TODO only for post
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

    public static class HttpRequestBuilder {

        public HttpRequestBuilder url(String url) {
            this.url = url;
            return this;
        }

        public HttpRequestBuilder url(String baseUrl, String path) { // TODO test
            if (baseUrl.endsWith("/")) {
                baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            }
            if (path.startsWith("/")) {
                path = path.substring(1);
            }
            this.url = baseUrl + "/" + path;
            return this;
        }

        public HttpRequestBuilder addHeader(String name, String value) {
            if (this.headers == null) {
                this.headers = new HashMap<>();
            }
            this.headers.put(name, value);
            return this;
        }
    }
}
