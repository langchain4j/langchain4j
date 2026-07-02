package dev.langchain4j.http.client;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.ValidationUtils.ensureBetween;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public class SuccessfulHttpResponse {

    private final int statusCode;
    private final Map<String, List<String>> headers;
    private final byte[] body;

    public SuccessfulHttpResponse(Builder builder) {
        this.statusCode = ensureBetween(builder.statusCode, 200, 299, "statusCode");
        this.headers = copy(builder.headers);
        this.body = builder.body;
    }

    public int statusCode() {
        return statusCode;
    }

    public Map<String, List<String>> headers() {
        return headers;
    }

    /**
     * @return the value of the {@code Content-Type} response header (case-insensitive), or null if absent.
     */
    public String contentType() {
        if (headers == null) {
            return null;
        }
        return headers.entrySet().stream()
                .filter(entry -> "content-type".equalsIgnoreCase(entry.getKey()))
                .map(Map.Entry::getValue)
                .filter(values -> values != null && !values.isEmpty())
                .map(values -> values.get(0))
                .findFirst()
                .orElse(null);
    }

    public String body() {
        return body == null ? null : new String(body, charset());
    }

    // TODO 2.0 Naming note: the byte[] is the canonical body; body() returns a decoded String view of it.
    // In 2.0 (when breaking changes are allowed) consider flipping this asymmetry to
    // byte[] body() + String bodyText(), and renaming this accessor accordingly.
    public byte[] bodyBytes() {
        return body;
    }

    /**
     * Determines the charset to decode the body with, based on the {@code charset} parameter of the
     * {@code Content-Type} response header, falling back to UTF-8 when it is absent or unsupported.
     */
    private Charset charset() {
        String contentType = contentType();
        if (contentType == null) {
            return StandardCharsets.UTF_8;
        }
        for (String part : contentType.split(";")) {
            String trimmed = part.trim();
            if (trimmed.regionMatches(true, 0, "charset=", 0, "charset=".length())) {
                String charsetName =
                        trimmed.substring("charset=".length()).trim().replaceAll("^\"|\"$", "");
                try {
                    return Charset.forName(charsetName);
                } catch (Exception e) {
                    return StandardCharsets.UTF_8;
                }
            }
        }
        return StandardCharsets.UTF_8;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private int statusCode;
        private Map<String, List<String>> headers;
        private byte[] body;

        private Builder() {}

        public Builder statusCode(int statusCode) {
            this.statusCode = statusCode;
            return this;
        }

        public Builder headers(Map<String, List<String>> headers) {
            this.headers = headers;
            return this;
        }

        public Builder body(byte[] body) {
            this.body = body;
            return this;
        }

        public Builder body(String body) {
            this.body = body == null ? null : body.getBytes(StandardCharsets.UTF_8);
            return this;
        }

        public SuccessfulHttpResponse build() {
            return new SuccessfulHttpResponse(this);
        }
    }
}
