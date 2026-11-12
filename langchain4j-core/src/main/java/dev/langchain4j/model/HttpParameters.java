package dev.langchain4j.model;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Parts of the HTTP request that are sent to the LLM provider with a single {@link ChatRequest},
 * on top of the request body that the other {@link ChatRequestParameters} make up.
 * <p>
 * It holds custom HTTP headers. Use them for values that change from call to call,
 * for example a correlation ID, a tracing header or a tenant ID:
 * <pre>{@code
 * ChatRequest chatRequest = ChatRequest.builder()
 *         .messages(UserMessage.from("Hello"))
 *         .customHeaders(Map.of("X-Correlation-Id", correlationId))
 *         .build();
 *
 * ChatResponse chatResponse = chatModel.chat(chatRequest);
 * }</pre>
 * Headers that are the same for every call are better set once, with the {@code customHeaders(...)} option
 * of the model builder.
 * <p>
 * Instances are immutable and safe to share between threads.
 * <p>
 * Not every {@link ChatModel} can send these.
 * The model integrations in the main LangChain4j repository that cannot send them fail the call
 * with an {@link UnsupportedFeatureException}. Other {@link ChatModel} implementations (for example community
 * integrations, or model classes of a framework integration) may not know about this class and ignore it.
 * Keep this in mind before you rely on a per-request header for authentication:
 * if it is ignored, the request is sent with the credentials of the model.
 *
 * @see ChatRequestParameters#httpParameters()
 * @since 1.24.0
 */
@Experimental
public final class HttpParameters {

    private final Map<String, String> customHeaders;

    private HttpParameters(Builder builder) {
        this.customHeaders = copy(validate(builder.customHeaders));
    }

    private static @Nullable Map<String, String> validate(@Nullable Map<String, String> customHeaders) {
        if (customHeaders == null) {
            return null;
        }
        Set<String> lowerCaseNames = new HashSet<>();
        customHeaders.forEach((name, value) -> {
            ensureNotBlank(name, "custom header name");
            ensureNotNull(value, "value of custom header '" + name + "'");
            if (!lowerCaseNames.add(name.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Custom header '" + name
                        + "' is set more than once with different letter case; HTTP header names ignore case,"
                        + " so set it only once");
            }
        });
        return customHeaders;
    }

    /**
     * Returns the custom HTTP headers to send with the request, as an unmodifiable map;
     * empty if there are none.
     * <p>
     * They are sent in addition to the headers that the model sends anyway and to the headers configured with the
     * {@code customHeaders(...)} option of the model builder. When a header with the same name (ignoring case)
     * is set in several places, the value set here wins, also over headers the model sets itself.
     * The exception is {@code User-Agent}: its value is a list of product tokens, so the value set here is added
     * after the model's value (separated by a space) instead of replacing it.
     * Some model integrations handle a few more headers their own way: for example, the Google integrations also
     * add to {@code x-goog-api-client}, and some provider SDKs always set the authentication header themselves.
     * The per-request HTTP headers section of the chat model documentation lists these cases.
     */
    public Map<String, String> customHeaders() {
        return customHeaders;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof HttpParameters that)) return false;
        return Objects.equals(customHeaders, that.customHeaders);
    }

    @Override
    public int hashCode() {
        return Objects.hash(customHeaders);
    }

    /**
     * Only header names are included; values are left out, as they may contain secrets.
     */
    @Override
    public String toString() {
        return "HttpParameters{customHeaders=" + customHeaders.keySet() + "}";
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Not thread-safe.
     */
    public static class Builder {

        private Map<String, String> customHeaders;

        /**
         * Sets the custom HTTP headers to send with the request. See {@link HttpParameters#customHeaders()}.
         * The map is copied when {@link #build()} is called. Header names must not be blank, values must not be
         * {@code null}, and a name must not appear twice with different letter case.
         *
         * @param customHeaders the headers; {@code null} means none
         */
        public Builder customHeaders(@Nullable Map<String, String> customHeaders) {
            this.customHeaders = customHeaders;
            return this;
        }

        public HttpParameters build() {
            return new HttpParameters(this);
        }
    }
}
