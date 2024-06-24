package dev.langchain4j.agent.tool;

import lombok.Builder;

import static dev.langchain4j.internal.Utils.getOrDefault;

/**
 * TODO
 */
@Builder
public class ToolParameterThingy {
    // TODO name
    // TODO location

    private final String type;
    private final String description;
    private final boolean required;

    public ToolParameterThingy(String type, String description, Boolean required) {
        this.type = type; // TODO
        this.description = description; // TODO
        this.required = getOrDefault(required, false);
    }

    public String type() {
        return type;
    }

    public String description() {
        return description;
    }

    public boolean required() { // TODO name
        return required;
    }
}
