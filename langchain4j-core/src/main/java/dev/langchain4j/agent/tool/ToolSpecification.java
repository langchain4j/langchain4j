package dev.langchain4j.agent.tool;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static java.util.Arrays.asList;

public class ToolSpecification {

    private final String name;
    private final String description;
    private final ToolParameters parameters;

    private ToolSpecification(Builder builder) {
        this.name = builder.name;
        this.description = builder.description;
        this.parameters = builder.parameters;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public ToolParameters parameters() {
        return parameters;
    }

    @Override
    public boolean equals(Object another) {
        if (this == another) return true;
        return another instanceof ToolSpecification
                && equalTo((ToolSpecification) another);
    }

    private boolean equalTo(ToolSpecification another) {
        return Objects.equals(name, another.name)
                && Objects.equals(description, another.description)
                && Objects.equals(parameters, another.parameters);
    }

    @Override
    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(name);
        h += (h << 5) + Objects.hashCode(description);
        h += (h << 5) + Objects.hashCode(parameters);
        return h;
    }

    @Override
    public String toString() {
        return "ToolSpecification {"
                + " name = " + name
                + ", description = " + description
                + ", parameters = " + parameters
                + " }";
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String name;
        private String description;
        private ToolParameters parameters;

        private Builder() {
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder parameters(ToolParameters parameters) {
            this.parameters = parameters;
            return this;
        }

        public Builder addParameter(String name, JsonSchemaProperty... jsonSchemaProperties) {
            return addParameter(name, asList(jsonSchemaProperties));
        }

        public Builder addParameter(String name, Iterable<JsonSchemaProperty> jsonSchemaProperties) {
            addOptionalParameter(name, jsonSchemaProperties);
            this.parameters.required().add(name);
            return this;
        }

        public Builder addOptionalParameter(String name, JsonSchemaProperty... jsonSchemaProperties) {
            return addOptionalParameter(name, asList(jsonSchemaProperties));
        }

        public Builder addOptionalParameter(String name, Iterable<JsonSchemaProperty> jsonSchemaProperties) {
            if (this.parameters == null) {
                this.parameters = ToolParameters.builder().build();
            }

            Map<String, Object> jsonSchemaPropertiesMap = new HashMap<>();
            for (JsonSchemaProperty jsonSchemaProperty : jsonSchemaProperties) {
                jsonSchemaPropertiesMap.put(jsonSchemaProperty.key(), jsonSchemaProperty.value());
            }

            this.parameters.properties().put(name, jsonSchemaPropertiesMap);
            return this;
        }

        public ToolSpecification build() {
            return new ToolSpecification(this);
        }
    }
}
