package dev.langchain4j.agent.tool;

import java.util.*;

import static dev.langchain4j.internal.Utils.quoted;
import static java.util.Arrays.asList;

public class ToolParameters {

    private final String type;
    private final Map<String, Map<String, Object>> properties;
    private final List<String> required;

    private ToolParameters(Builder builder) {
        this.type = builder.type;
        this.properties = builder.properties;
        this.required = builder.required;
    }

    public String type() {
        return type;
    }

    public Map<String, Map<String, Object>> properties() {
        return properties;
    }

    public List<String> required() {
        return required;
    }

    @Override
    public boolean equals(Object another) {
        if (this == another) return true;
        return another instanceof ToolParameters
                && equalTo((ToolParameters) another);
    }

    private boolean equalTo(ToolParameters another) {
        return Objects.equals(type, another.type)
                && Objects.equals(properties, another.properties)
                && Objects.equals(required, another.required);
    }

    @Override
    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(type);
        h += (h << 5) + Objects.hashCode(properties);
        h += (h << 5) + Objects.hashCode(required);
        return h;
    }

    @Override
    public String toString() {
        return "ToolParameters {"
                + " type = " + quoted(type)
                + ", properties = " + properties
                + ", required = " + required
                + " }";
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String type = "object";
        private Map<String, Map<String, Object>> properties = new HashMap<>();
        private List<String> required = new ArrayList<>();

        private Builder() {
        }

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder addParameter(String name, JsonSchemaProperty... jsonSchemaProperties) {
            addParameter(name, asList(jsonSchemaProperties));
            return this;
        }

        public Builder addParameter(String name, Iterable<JsonSchemaProperty> jsonSchemaProperties) {
            addOptionalParameter(name, jsonSchemaProperties);
            this.required.add(name);
            return this;
        }

        public Builder addOptionalParameter(String name, JsonSchemaProperty... jsonSchemaProperties) {
            addOptionalParameter(name, asList(jsonSchemaProperties));
            return this;
        }

        public Builder addOptionalParameter(String name, Iterable<JsonSchemaProperty> jsonSchemaProperties) {
            Map<String, Object> jsonSchemaPropertiesMap = new HashMap<>();
            for (JsonSchemaProperty jsonSchemaProperty : jsonSchemaProperties) {
                jsonSchemaPropertiesMap.put(jsonSchemaProperty.key(), jsonSchemaProperty.value());
            }

            this.properties.put(name, jsonSchemaPropertiesMap);
            return this;
        }

        public ToolParameters build() {
            return new ToolParameters(this);
        }
    }
}
