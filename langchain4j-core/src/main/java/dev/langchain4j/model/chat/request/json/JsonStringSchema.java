package dev.langchain4j.model.chat.request.json;

import static dev.langchain4j.internal.Utils.quoted;

import java.util.Objects;

public class JsonStringSchema implements JsonSchemaElement {

    private final String description;
    private final Integer minLength;
    private final Integer maxLength;
    private final String pattern;
    private final String format;

    public JsonStringSchema() {
        this.description = null;
        this.minLength = null;
        this.maxLength = null;
        this.pattern = null;
        this.format = null;
    }

    public JsonStringSchema(Builder builder) {
        this.description = builder.description;
        this.minLength = builder.minLength;
        this.maxLength = builder.maxLength;
        this.pattern = builder.pattern;
        this.format = builder.format;
    }

    @Override
    public String description() {
        return description;
    }

    public Integer minLength() {
        return minLength;
    }

    public Integer maxLength() {
        return maxLength;
    }

    public String pattern() {
        return pattern;
    }

    public String format() {
        return format;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String description;
        private Integer minLength;
        private Integer maxLength;
        private String pattern;
        private String format;

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder minLength(Integer minLength) {
            this.minLength = minLength;
            return this;
        }

        public Builder maxLength(Integer maxLength) {
            this.maxLength = maxLength;
            return this;
        }

        public Builder pattern(String pattern) {
            this.pattern = pattern;
            return this;
        }

        public Builder format(String format) {
            this.format = format;
            return this;
        }

        public JsonStringSchema build() {
            return new JsonStringSchema(this);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JsonStringSchema that = (JsonStringSchema) o;
        return Objects.equals(this.description, that.description)
                && Objects.equals(this.minLength, that.minLength)
                && Objects.equals(this.maxLength, that.maxLength)
                && Objects.equals(this.pattern, that.pattern)
                && Objects.equals(this.format, that.format);
    }

    @Override
    public int hashCode() {
        return Objects.hash(description, minLength, maxLength, pattern, format);
    }

    @Override
    public String toString() {
        return "JsonStringSchema {" + "description = "
                + quoted(description) + ", minLength = "
                + minLength + ", maxLength = "
                + maxLength + ", pattern = "
                + quoted(pattern) + ", format = "
                + quoted(format) + " }";
    }
}
