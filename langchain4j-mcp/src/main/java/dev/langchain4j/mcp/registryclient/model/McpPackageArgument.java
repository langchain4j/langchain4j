package dev.langchain4j.mcp.registryclient.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

public class McpPackageArgument {

    private List<String> choices;

    @JsonProperty("default")
    private String defaultValue;

    private String description;
    private String format;

    @JsonProperty("is_repeated")
    private boolean isRepeated;

    @JsonProperty("is_required")
    private boolean isRequired;

    @JsonProperty("is_secret")
    private boolean isSecret;

    private String name;
    private String type;
    private String value;

    @JsonProperty("value_hint")
    private String valueHint;

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

    public boolean isRepeated() {
        return isRepeated;
    }

    public boolean isRequired() {
        return isRequired;
    }

    public boolean isSecret() {
        return isSecret;
    }

    public String getType() {
        return type;
    }

    public String getValueHint() {
        return valueHint;
    }
}
