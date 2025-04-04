package dev.langchain4j.model.openai.internal.chat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import java.util.List;
import java.util.Objects;

@JsonDeserialize(builder = JsonAnyOfSchema.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class JsonAnyOfSchema extends JsonSchemaElement {

    @JsonProperty
    private final String description;

    @JsonProperty("anyOf")
    private final List<JsonSchemaElement> anyOf;

    public JsonAnyOfSchema(Builder builder) {
        super((String) null);
        this.description = builder.description;
        this.anyOf = builder.anyOf;
    }

    @Override
    public boolean equals(final Object another) {
        if (this == another) return true;
        return another instanceof JsonAnyOfSchema && equalTo((JsonAnyOfSchema) another);
    }

    private boolean equalTo(final JsonAnyOfSchema another) {
        return Objects.equals(description, another.description) && Objects.equals(anyOf, another.anyOf);
    }

    @Override
    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(description);
        h += (h << 5) + Objects.hashCode(anyOf);
        return h;
    }

    @Override
    public String toString() {
        return "JsonAnyOfSchema{" + "description='" + description + '\'' + ", anyOf=" + anyOf + '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class Builder {

        private String description;
        private List<JsonSchemaElement> anyOf;

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder anyOf(List<JsonSchemaElement> anyOf) {
            this.anyOf = anyOf;
            return this;
        }

        public JsonAnyOfSchema build() {
            return new JsonAnyOfSchema(this);
        }
    }
}
