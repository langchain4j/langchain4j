package dev.langchain4j.mcp.registryclient.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

public class McpEnvironmentVariable {
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

    public boolean isRequired() {
        return isRequired;
    }

    public boolean isSecret() {
        return isSecret;
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

    @Override
    public String toString() {
        return "McpEnvironmentVariable{" +
                "choices=" + choices +
                ", defaultValue='" + defaultValue + '\'' +
                ", description='" + description + '\'' +
                ", format='" + format + '\'' +
                ", isRequired=" + isRequired +
                ", isSecret=" + isSecret +
                ", name='" + name + '\'' +
                ", value='" + value + '\'' +
                ", variables=" + variables +
                '}';
    }
}
