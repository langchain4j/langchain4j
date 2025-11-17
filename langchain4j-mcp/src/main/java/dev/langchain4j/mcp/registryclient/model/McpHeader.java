package dev.langchain4j.mcp.registryclient.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

public class McpHeader {

    private List<String> choices;

    @JsonProperty("default")
    private String defaultValue;

    private String description;
    private String format;

    @JsonAlias("is_required")
    private boolean isRequired;

    @JsonAlias("is_secret")
    private boolean isSecret;

    private String name;
    private String value;
    private Map<String, McpVariable> variables;

    public List<String> getChoices() {
        return choices;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public String getDescription() {
        return description;
    }

    public String getFormat() {
        return format;
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }

    public Map<String, McpVariable> getVariables() {
        return variables;
    }

    public boolean isRequired() {
        return isRequired;
    }

    public boolean isSecret() {
        return isSecret;
    }

    @Override
    public String toString() {
        String maskedDefaultValue = isSecret ? "[REDACTED]" : defaultValue;
        String maskedValue = isSecret ? "[REDACTED]" : value;

        return "McpHeader{" + "choices="
                + choices + ", defaultValue='"
                + maskedDefaultValue + '\'' + ", description='"
                + description + '\'' + ", format='"
                + format + '\'' + ", isRequired="
                + isRequired + ", isSecret="
                + isSecret + ", name='"
                + name + '\'' + ", value='"
                + maskedValue + '\'' + ", variables="
                + variables + '}';
    }
}
