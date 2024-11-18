package dev.langchain4j.model.azure.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.Objects;

@JsonDeserialize(builder = AzureJsonArraySchema.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AzureJsonArraySchema extends AzureJsonSchemaElement {

    @JsonProperty
    private final String description;
    @JsonProperty
    private final AzureJsonSchemaElement items;

    public AzureJsonArraySchema(Builder builder) {
        super("array");
        this.description = builder.description;
        this.items = builder.items;
    }

    @Override
    public boolean equals(Object another) {
        if (this == another) return true;
        return another instanceof AzureJsonArraySchema
                && equalTo((AzureJsonArraySchema) another);
    }

    private boolean equalTo(AzureJsonArraySchema another) {
        return Objects.equals(description, another.description)
                && Objects.equals(items, another.items);
    }

    @Override
    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(description);
        h += (h << 5) + Objects.hashCode(items);
        return h;
    }

    @Override
    public String toString() {
        return "JsonArraySchema{" +
                "description=" + description +
                ", items=" + items +
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
        private AzureJsonSchemaElement items;

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder items(AzureJsonSchemaElement items) {
            this.items = items;
            return this;
        }

        public AzureJsonArraySchema build() {
            return new AzureJsonArraySchema(this);
        }
    }
}
