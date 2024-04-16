package dev.langchain4j.agent.tool;

import dev.langchain4j.jsonschema.JsonSchema;

import java.util.Map;

/**
 * The JsonSchema that describes a {@link Tool}.
 */
public class ToolJsonSchema {
    private final String name;
    private final String description;
    private final Map<String, JsonSchema> parameters;

    /**
     * Creates a {@link ToolJsonSchema}.
     */
    ToolJsonSchema(String name, String description, Map<String, JsonSchema> parameters) {
        this.name = name;
        this.description = description;
        this.parameters = parameters;
    }

    /** Get the name of the {@link Tool}. */
    public String name() {
        return name;
    }

    /** Get the description of the {@link Tool}. */
    public String description() {
        return description;
    }

    /** Get the parameters of the {@link Tool}. */
    public Map<String, JsonSchema> parameters() {
        return parameters;
    }

    /**
     * Convert this JsonSchema to {@link ToolSpecification}.
     *
     * @return the {@link ToolSpecification}.
     */
    public ToolSpecification toToolSpecification() {
        ToolSpecification.Builder builder =
                ToolSpecification.builder().name(this.name).description(this.description);
        for (Map.Entry<String, JsonSchema> entry : this.parameters.entrySet()) {
            builder.addParameter(entry.getKey(), entry.getValue().getProperties());
        }
        return builder.build();
    }
}
