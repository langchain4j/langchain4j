package dev.langchain4j.model.openai.internal.chat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.Objects;

/**
 * Denotes that an array can contain items of multiple types. See
 * https://cswr.github.io/JsonSchema/spec/multiple_types/
 *
 * Example of an array that allows 4 different types of items:
 *
 * "query-params": {
 *   "type": "array",
 *   "items": {
 *     "type": [
 *       "string",
 *       "number",
 *       "boolean",
 *       "null"
 *     ]
 *   },
 *   "description": "Query parameters (optional)"
 * }
 *
 */
@JsonDeserialize(builder = JsonTypeArraySchema.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class JsonTypeArraySchema extends JsonSchemaElement {

    public JsonTypeArraySchema(Builder builder) {
        super(builder.types);
    }

    @Override
    public boolean equals(Object another) {
        if (this == another) return true;
        return another instanceof JsonTypeArraySchema
                && equalTo((JsonTypeArraySchema) another);
    }

    private boolean equalTo(JsonTypeArraySchema another) {
        return Objects.equals(super.type(), another.type());
    }

    @Override
    public String toString() {
        return "JsonTypeArraySchema{" +
                "types=" + super.type() +
                "}";
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class Builder {

        private String[] types;

        public Builder types(String[] types) {
            this.types = types;
            return this;
        }

        public JsonTypeArraySchema build() {
            return new JsonTypeArraySchema(this);
        }
    }
}
