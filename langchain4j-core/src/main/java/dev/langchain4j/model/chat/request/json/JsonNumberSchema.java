package dev.langchain4j.model.chat.request.json;

import static dev.langchain4j.internal.Utils.quoted;

import java.util.Objects;

public class JsonNumberSchema implements JsonSchemaElement {

    private final String description;
    private final Double minimum;
    private final Double maximum;
    private final Double exclusiveMinimum;
    private final Double exclusiveMaximum;

    public JsonNumberSchema() {
        this.description = null;
        this.minimum = null;
        this.maximum = null;
        this.exclusiveMinimum = null;
        this.exclusiveMaximum = null;
    }

    public JsonNumberSchema(Builder builder) {
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

    public Double minimum() {
        return minimum;
    }

    public Double maximum() {
        return maximum;
    }

    public Double exclusiveMinimum() {
        return exclusiveMinimum;
    }

    public Double exclusiveMaximum() {
        return exclusiveMaximum;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String description;
        private Double minimum;
        private Double maximum;
        private Double exclusiveMinimum;
        private Double exclusiveMaximum;

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder minimum(Double minimum) {
            this.minimum = minimum;
            return this;
        }

        public Builder maximum(Double maximum) {
            this.maximum = maximum;
            return this;
        }

        public Builder exclusiveMinimum(Double exclusiveMinimum) {
            this.exclusiveMinimum = exclusiveMinimum;
            return this;
        }

        public Builder exclusiveMaximum(Double exclusiveMaximum) {
            this.exclusiveMaximum = exclusiveMaximum;
            return this;
        }

        public JsonNumberSchema build() {
            return new JsonNumberSchema(this);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JsonNumberSchema that = (JsonNumberSchema) o;
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
        return "JsonNumberSchema {" + "description = "
                + quoted(description) + ", minimum = "
                + minimum + ", maximum = "
                + maximum + ", exclusiveMinimum = "
                + exclusiveMinimum + ", exclusiveMaximum = "
                + exclusiveMaximum + " }";
    }
}
