package dev.langchain4j.model.openai.internal.chat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

@JsonDeserialize(builder = JsonNullSchema.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class JsonNullSchema extends JsonSchemaElement {

    public JsonNullSchema(Builder builder) {
        super("null");
    }

    @Override
    public boolean equals(Object another) {
        if (this == another) return true;
        return another instanceof JsonNullSchema && equalTo((JsonNullSchema) another);
    }

    private boolean equalTo(JsonNullSchema another) {
        return true;
    }

    @Override
    public String toString() {
        return "JsonNullSchema{}";
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class Builder {

        public JsonNullSchema build() {
            return new JsonNullSchema(this);
        }
    }
}
