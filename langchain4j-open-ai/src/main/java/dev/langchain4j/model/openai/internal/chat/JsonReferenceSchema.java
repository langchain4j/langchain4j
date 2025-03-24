package dev.langchain4j.model.openai.internal.chat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import java.util.Objects;

@JsonDeserialize(builder = JsonReferenceSchema.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class JsonReferenceSchema extends JsonSchemaElement {

    @JsonProperty("$ref")
    private final String reference;

    public JsonReferenceSchema(Builder builder) {
        super((String) null);
        this.reference = builder.reference;
    }

    @Override
    public boolean equals(Object another) {
        if (this == another) return true;
        return another instanceof JsonReferenceSchema && equalTo((JsonReferenceSchema) another);
    }

    private boolean equalTo(JsonReferenceSchema another) {
        return Objects.equals(reference, another.reference);
    }

    @Override
    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(reference);
        return h;
    }

    @Override
    public String toString() {
        return "JsonReferenceSchema{" + "reference=" + reference + "}";
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class Builder {

        private String reference;

        public Builder reference(String reference) {
            this.reference = reference;
            return this;
        }

        public JsonReferenceSchema build() {
            return new JsonReferenceSchema(this);
        }
    }
}
