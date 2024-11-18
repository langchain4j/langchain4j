package dev.langchain4j.model.azure.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.Objects;

@JsonDeserialize(builder = AzureJsonStringSchema.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AzureJsonStringSchema extends AzureJsonSchemaElement {

    @JsonProperty
    private final String description;

    public AzureJsonStringSchema(Builder builder) {
        super("string");
        this.description = builder.description;
    }

    @Override
    public boolean equals(Object another) {
        if (this == another) return true;
        return another instanceof AzureJsonStringSchema
                && equalTo((AzureJsonStringSchema) another);
    }

    private boolean equalTo(AzureJsonStringSchema another) {
        return Objects.equals(description, another.description);
    }

    @Override
    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(description);
        return h;
    }

    @Override
    public String toString() {
        return "JsonStringSchema{" +
                "description=" + description +
                "}";
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class Builder {

        private String description;

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public AzureJsonStringSchema build() {
            return new AzureJsonStringSchema(this);
        }
    }
}
