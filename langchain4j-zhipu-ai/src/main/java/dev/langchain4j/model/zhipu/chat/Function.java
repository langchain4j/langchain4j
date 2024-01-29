package dev.langchain4j.model.zhipu.chat;

import dev.langchain4j.agent.tool.JsonSchemaProperty;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class Function {

    private final String name;
    private final String description;
    private final Parameters parameters;

    private Function(Builder builder) {
        this.name = builder.name;
        this.description = builder.description;
        this.parameters = builder.parameters;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String name() {
        return this.name;
    }

    public String description() {
        return this.description;
    }

    public Parameters parameters() {
        return this.parameters;
    }

    @Override
    public boolean equals(Object another) {
        if (this == another) {
            return true;
        } else {
            return another instanceof Function && this.equalTo((Function) another);
        }
    }

    private boolean equalTo(Function another) {
        return Objects.equals(this.name, another.name)
                && Objects.equals(this.description, another.description)
                && Objects.equals(this.parameters, another.parameters);
    }

    @Override
    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(this.name);
        h += (h << 5) + Objects.hashCode(this.description);
        h += (h << 5) + Objects.hashCode(this.parameters);
        return h;
    }

    @Override
    public String toString() {
        return "Function{"
                + "name=" + this.name
                + ", description=" + this.description
                + ", parameters=" + this.parameters
                + "}";
    }

    public static final class Builder {
        private String name;
        private String description;
        private Parameters parameters;

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

        public Builder parameters(Parameters parameters) {
            this.parameters = parameters;
            return this;
        }

        public Builder addParameter(String name, JsonSchemaProperty... jsonSchemaProperties) {
            this.addOptionalParameter(name, jsonSchemaProperties);
            this.parameters.required().add(name);
            return this;
        }

        public Builder addOptionalParameter(String name, JsonSchemaProperty... jsonSchemaProperties) {
            if (this.parameters == null) {
                this.parameters = Parameters.builder().build();
            }

            Map<String, Object> jsonSchemaPropertiesMap = new HashMap<>();

            for (JsonSchemaProperty jsonSchemaProperty : jsonSchemaProperties) {
                jsonSchemaPropertiesMap.put(jsonSchemaProperty.key(), jsonSchemaProperty.value());
            }

            this.parameters.properties().put(name, jsonSchemaPropertiesMap);
            return this;
        }

        public Function build() {
            return new Function(this);
        }
    }
}
