package dev.langchain4j.mcp.registryclient.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

public class McpPackageArgument {

    private List<String> choices;

    @JsonProperty("default")
    private String defaultValue;

    private String description;
    private String format;

    @JsonAlias("is_repeated")
    private boolean isRepeated;

    @JsonAlias("is_required")
    private boolean isRequired;

    @JsonAlias("is_secret")
    private boolean isSecret;

    private String name;
    private String type;
    private String value;

    @JsonAlias("value_hint")
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

    @Override
    public String toString() {
        String maskedDefaultValue = isSecret ? "<redacted>" : defaultValue;
        String maskedValue = isSecret ? "<redacted>" : value;

        return "McpPackageArgument{" + "choices="
                + choices + ", defaultValue='"
                + maskedDefaultValue + '\'' + ", description='"
                + description + '\'' + ", format='"
                + format + '\'' + ", isRepeated="
                + isRepeated + ", isRequired="
                + isRequired + ", isSecret="
                + isSecret + ", name='"
                + name + '\'' + ", type='"
                + type + '\'' + ", value='"
                + maskedValue + '\'' + ", valueHint='"
                + valueHint + '\'' + ", variables="
                + variables + '}';
    }
}
