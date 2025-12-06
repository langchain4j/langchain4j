package dev.langchain4j.http.client;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.joining;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class HttpRequest {

    private final HttpMethod method;
    private final String url;
    private final Map<String, List<String>> headers;
    private final Map<String, String> formData;
    private final Map<String, MultipartFile> files;
    private final String body;

    public HttpRequest(Builder builder) {
        this.method = ensureNotNull(builder.method, "method");
        this.url = buildUrl(builder);
        this.headers = copy(builder.headers);
        this.formData = copy(builder.formData);
        this.files = copy(builder.files);
        this.body = builder.body;
    }

    private static String buildUrl(Builder builder) {
        String url = ensureNotBlank(builder.url, "url");
        if (isNullOrEmpty(builder.queryParams)) {
            return url;
        }

        String queryString = buildQueryString(builder.queryParams);
        String separator = url.contains("?") ? "&" : "?";
        return url + separator + queryString;
    }

    private static String buildQueryString(Map<String, String> queryParams) {
        return queryParams.entrySet().stream()
                .map(entry -> encodeQueryParam(entry.getKey()) + "=" + encodeQueryParam(entry.getValue()))
                .collect(joining("&"));
    }

    private static String encodeQueryParam(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
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

    public Map<String, String> formData() {
        return formData;
    }

    public Map<String, MultipartFile> files() {
        return files;
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
        private Map<String, String> queryParams;
        private Map<String, String> formData;
        private Map<String, MultipartFile> files;
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

        public Builder addQueryParam(String name, String value) {
            ensureNotBlank(name, "name");
            ensureNotBlank(value, "value");

            if (this.queryParams == null) {
                this.queryParams = new LinkedHashMap<>();
            }
            this.queryParams.put(name, value);
            return this;
        }

        public Builder addQueryParams(Map<String, String> queryParams) {
            if (isNullOrEmpty(queryParams)) {
                return this;
            }
            if (this.queryParams == null) {
                this.queryParams = new LinkedHashMap<>();
            }
            this.queryParams.putAll(queryParams);
            return this;
        }

        public Builder queryParams(Map<String, String> queryParams) {
            if (queryParams == null) {
                this.queryParams = null;
            } else {
                this.queryParams = new LinkedHashMap<>(queryParams);
            }
            return this;
        }

        public Builder addFormData(String name, String value) {
            ensureNotBlank(name, "name");
            ensureNotNull(value, "value");

            if (this.formData == null) {
                this.formData = new LinkedHashMap<>();
            }
            this.formData.put(name, value);
            return this;
        }

        public Builder addFormData(Map<String, String> formData) {
            if (isNullOrEmpty(formData)) {
                return this;
            }
            if (this.formData == null) {
                this.formData = new LinkedHashMap<>();
            }
            this.formData.putAll(formData);
            return this;
        }

        public Builder formData(Map<String, String> formData) {
            if (formData == null) {
                this.formData = null;
            } else {
                this.formData = new LinkedHashMap<>(formData);
            }
            return this;
        }

        public Builder addFile(String name, String filename, String contentType, byte[] fileAsBytes) {
            if (fileAsBytes.length == 0) {
                return this;
            }
            if (this.files == null) {
                this.files = new LinkedHashMap<>();
            }
            this.files.put(name, new MultipartFile(filename, contentType, fileAsBytes));
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
