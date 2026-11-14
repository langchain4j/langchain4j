package dev.langchain4j.model.chat.request.json;

import static dev.langchain4j.internal.Utils.quoted;

import java.util.Objects;

public class JsonArraySchema implements JsonSchemaElement {

    private final String description;
    private final JsonSchemaElement items;
    private final Integer minItems;
    private final Integer maxItems;
    private final Boolean uniqueItems;

    public JsonArraySchema(Builder builder) {
        this.description = builder.description;
        this.items = builder.items;
        this.minItems = builder.minItems;
        this.maxItems = builder.maxItems;
        this.uniqueItems = builder.uniqueItems;
    }

    @Override
    public String description() {
        return description;
    }

    public JsonSchemaElement items() {
        return items;
    }

    public Integer minItems() {
        return minItems;
    }

    public Integer maxItems() {
        return maxItems;
    }

    public Boolean uniqueItems() {
        return uniqueItems;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String description;
        private JsonSchemaElement items;
        private Integer minItems;
        private Integer maxItems;
        private Boolean uniqueItems;

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder items(JsonSchemaElement items) {
            this.items = items;
            return this;
        }

        public Builder minItems(Integer minItems) {
            this.minItems = minItems;
            return this;
        }

        public Builder maxItems(Integer maxItems) {
            this.maxItems = maxItems;
            return this;
        }

        public Builder uniqueItems(Boolean uniqueItems) {
            this.uniqueItems = uniqueItems;
            return this;
        }

        public JsonArraySchema build() {
            return new JsonArraySchema(this);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JsonArraySchema that = (JsonArraySchema) o;
        return Objects.equals(this.description, that.description)
                && Objects.equals(this.items, that.items)
                && Objects.equals(this.minItems, that.minItems)
                && Objects.equals(this.maxItems, that.maxItems)
                && Objects.equals(this.uniqueItems, that.uniqueItems);
    }

    @Override
    public int hashCode() {
        return Objects.hash(description, items, minItems, maxItems, uniqueItems);
    }

    @Override
    public String toString() {
        return "JsonArraySchema {" + "description = "
                + quoted(description) + ", items = "
                + items + ", minItems = "
                + minItems + ", maxItems = "
                + maxItems + ", uniqueItems = "
                + uniqueItems + " }";
    }
}
