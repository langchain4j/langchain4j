package dev.langchain4j.model.azure.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.Objects;

@JsonDeserialize(builder = AzureJsonSchema.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AzureJsonSchema {

    @JsonProperty
    private final String name;
    @JsonProperty
    private final Boolean strict;
    @JsonProperty
    private final AzureJsonObjectSchema schema;

    public AzureJsonSchema(Builder builder) {
        this.name = builder.name;
        this.strict = builder.strict;
        this.schema = builder.schema;
    }

    @Override
    public boolean equals(Object another) {
        if (this == another) return true;
        return another instanceof AzureJsonSchema
                && equalTo((AzureJsonSchema) another);
    }

    private boolean equalTo(AzureJsonSchema another) {
        return Objects.equals(name, another.name)
                && Objects.equals(strict, another.strict)
                && Objects.equals(schema, another.schema);
    }

    @Override
    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(name);
        h += (h << 5) + Objects.hashCode(strict);
        h += (h << 5) + Objects.hashCode(schema);
        return h;
    }

    @Override
    public String toString() {
        return "JsonSchema{" +
                "name=" + name +
                ", strict=" + strict +
                ", schema=" + schema +
                "}";
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class Builder {

        private String name;
        private Boolean strict;
        private AzureJsonObjectSchema schema;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder strict(Boolean strict) {
            this.strict = strict;
            return this;
        }

        public Builder schema(AzureJsonObjectSchema schema) {
            this.schema = schema;
            return this;
        }

        public AzureJsonSchema build() {
            return new AzureJsonSchema(this);
        }
    }
}
