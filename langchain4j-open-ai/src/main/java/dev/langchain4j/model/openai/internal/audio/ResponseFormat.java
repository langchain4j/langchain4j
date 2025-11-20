package dev.langchain4j.model.openai.internal.audio;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import java.util.Objects;

public class ResponseFormat {

    public static final ResponseFormat TEXT =
            ResponseFormat.builder().type(ResponseFormatType.TEXT).build();
    public static final ResponseFormat JSON =
            ResponseFormat.builder().type(ResponseFormatType.JSON).build();
    public static final ResponseFormat SRT =
            ResponseFormat.builder().type(ResponseFormatType.SRT).build();
    public static final ResponseFormat VERBOSE_JSON =
            ResponseFormat.builder().type(ResponseFormatType.VERBOSE_JSON).build();
    public static final ResponseFormat VTT =
            ResponseFormat.builder().type(ResponseFormatType.VTT).build();
    public static final ResponseFormat DIARIZED_JSON =
            ResponseFormat.builder().type(ResponseFormatType.DIARIZED_JSON).build();

    private final ResponseFormatType type;

    private ResponseFormat(Builder builder) {
        this.type = ensureNotNull(builder.type, "type");
    }

    public ResponseFormatType type() {
        return type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ResponseFormat that = (ResponseFormat) o;
        return Objects.equals(this.type, that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type);
    }

    @Override
    public String toString() {
        return "ResponseFormat { type = " + type + " }";
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private ResponseFormatType type;

        public Builder type(ResponseFormatType type) {
            this.type = type;
            return this;
        }

        public ResponseFormat build() {
            return new ResponseFormat(this);
        }
    }
}
