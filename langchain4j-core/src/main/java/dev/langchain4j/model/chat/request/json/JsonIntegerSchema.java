package dev.langchain4j.model.chat.request.json;

import static dev.langchain4j.internal.Utils.quoted;

import java.util.Objects;

public class JsonIntegerSchema implements JsonSchemaElement {

    private final String description;
    private final Long minimum;
    private final Long maximum;
    private final Long exclusiveMinimum;
    private final Long exclusiveMaximum;

    public JsonIntegerSchema() {
        this.description = null;
        this.minimum = null;
        this.maximum = null;
        this.exclusiveMinimum = null;
        this.exclusiveMaximum = null;
    }

    public JsonIntegerSchema(Builder builder) {
        this.description = builder.description;
        this.minimum = builder.minimum;
        this.maximum = builder.maximum;
        this.exclusiveMinimum = builder.exclusiveMinimum;
        this.exclusiveMaximum = builder.exclusiveMaximum;
    }

    @Override
    public String description() {
        return description;
    }

    public Long minimum() {
        return minimum;
    }

    public Long maximum() {
        return maximum;
    }

    public Long exclusiveMinimum() {
        return exclusiveMinimum;
    }

    public Long exclusiveMaximum() {
        return exclusiveMaximum;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String description;
        private Long minimum;
        private Long maximum;
        private Long exclusiveMinimum;
        private Long exclusiveMaximum;

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder minimum(Long minimum) {
            this.minimum = minimum;
            return this;
        }

        public Builder maximum(Long maximum) {
            this.maximum = maximum;
            return this;
        }

        public Builder exclusiveMinimum(Long exclusiveMinimum) {
            this.exclusiveMinimum = exclusiveMinimum;
            return this;
        }

        public Builder exclusiveMaximum(Long exclusiveMaximum) {
            this.exclusiveMaximum = exclusiveMaximum;
            return this;
        }

        public JsonIntegerSchema build() {
            return new JsonIntegerSchema(this);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JsonIntegerSchema that = (JsonIntegerSchema) o;
        return Objects.equals(this.description, that.description)
                && Objects.equals(this.minimum, that.minimum)
                && Objects.equals(this.maximum, that.maximum)
                && Objects.equals(this.exclusiveMinimum, that.exclusiveMinimum)
                && Objects.equals(this.exclusiveMaximum, that.exclusiveMaximum);
    }

    @Override
    public int hashCode() {
        return Objects.hash(description, minimum, maximum, exclusiveMinimum, exclusiveMaximum);
    }

    @Override
    public String toString() {
        return "JsonIntegerSchema {" + "description = "
                + quoted(description) + ", minimum = "
                + minimum + ", maximum = "
                + maximum + ", exclusiveMinimum = "
                + exclusiveMinimum + ", exclusiveMaximum = "
                + exclusiveMaximum + " }";
    }
}
