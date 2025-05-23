package dev.langchain4j.mcp.client.transport;

public class PresetParameter {

    private final String name;

    private final String parameters;


    public PresetParameter(final String name, final String parameters) {
        this.name = name;
        this.parameters = parameters;
    }

    public String getName() {
        return name;
    }

    public String getParameters() {
        return parameters;
    }
}
