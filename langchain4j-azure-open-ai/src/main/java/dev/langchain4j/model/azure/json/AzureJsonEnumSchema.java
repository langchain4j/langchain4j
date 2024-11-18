package dev.langchain4j.model.azure.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toList;

@JsonDeserialize(builder = AzureJsonEnumSchema.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AzureJsonEnumSchema extends AzureJsonSchemaElement {

    @JsonProperty
    private final String description;
    @JsonProperty("enum")
    private final List<String> enumValues;

    public AzureJsonEnumSchema(Builder builder) {
        super("string");
        this.description = builder.description;
        this.enumValues = new ArrayList<>(builder.enumValues);
    }

    @Override
    public boolean equals(Object another) {
        if (this == another) return true;
        return another instanceof AzureJsonEnumSchema
                && equalTo((AzureJsonEnumSchema) another);
    }

    private boolean equalTo(AzureJsonEnumSchema another) {
        return Objects.equals(description, another.description)
                && Objects.equals(enumValues, another.enumValues);
    }

    @Override
    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(description);
        h += (h << 5) + Objects.hashCode(enumValues);
        return h;
    }

    @Override
    public String toString() {
        return "JsonEnumSchema{" +
                "description=" + description +
                ", enumValues=" + enumValues +
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
        private List<String> enumValues;

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder enumValues(List<String> enumValues) {
            this.enumValues = enumValues;
            return this;
        }

        public Builder enumValues(Class<?> enumClass) {
            if (!enumClass.isEnum()) {
                throw new RuntimeException("Class " + enumClass.getName() + " must be enum");
            }

            List<String> enumValues = stream(enumClass.getEnumConstants())
                    .map(Object::toString)
                    .collect(toList());

            return enumValues(enumValues);
        }

        public AzureJsonEnumSchema build() {
            return new AzureJsonEnumSchema(this);
        }
    }
}
