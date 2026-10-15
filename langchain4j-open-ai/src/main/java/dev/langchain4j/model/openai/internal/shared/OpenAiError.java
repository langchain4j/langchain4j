package dev.langchain4j.model.openai.internal.shared;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import dev.langchain4j.internal.JacocoIgnoreCoverageGenerated;
import java.util.Objects;

/**
 * OpenAI-compatible error object carried either as an HTTP error body or as a mid-stream SSE data chunk
 * (vLLM / LiteLLM bare {@code {"error":...}} and OpenRouter {@code chat.completion.chunk} with a top-level
 * {@code error} field).
 */
@JsonDeserialize(builder = OpenAiError.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class OpenAiError {

    @JsonProperty
    private final String message;

    @JsonProperty
    private final String type;

    @JsonProperty
    private final String param;
    /**
     * Provider-specific code. May be a JSON string ({@code "server_error"}) or number ({@code 500});
     * stored as {@link Object} so both wire shapes deserialize.
     */
    @JsonProperty
    private final Object code;

    @JsonCreator
    public OpenAiError(Builder builder) {
        this.message = builder.message;
        this.type = builder.type;
        this.param = builder.param;
        this.code = builder.code;
    }

    public String message() {
        return message;
    }

    public String type() {
        return type;
    }

    public String param() {
        return param;
    }

    public String code() {
        return code == null ? null : String.valueOf(code);
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public boolean equals(Object another) {
        if (this == another) return true;
        return another instanceof OpenAiError && equalTo((OpenAiError) another);
    }

    @JacocoIgnoreCoverageGenerated
    private boolean equalTo(OpenAiError another) {
        return Objects.equals(message, another.message)
                && Objects.equals(type, another.type)
                && Objects.equals(param, another.param)
                && Objects.equals(code(), another.code());
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(message);
        h += (h << 5) + Objects.hashCode(type);
        h += (h << 5) + Objects.hashCode(param);
        h += (h << 5) + Objects.hashCode(code());
        return h;
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public String toString() {
        return "OpenAiError{" + "message=" + message + ", type=" + type + ", param=" + param + ", code=" + code() + "}";
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    public static final class Builder {

        private String message;
        private String type;
        private String param;
        private Object code;

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder param(String param) {
            this.param = param;
            return this;
        }

        public Builder code(Object code) {
            this.code = code;
            return this;
        }

        public OpenAiError build() {
            return new OpenAiError(this);
        }
    }
}
