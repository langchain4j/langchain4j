package dev.langchain4j.mcp.registryclient.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class McpVariable {

    private List<String> choices;

    @JsonProperty("default")
    private String defaultValue;

    private String description;
    private String format;

    @JsonProperty("is_required")
    private boolean isRequired;

    @JsonProperty("is_secret")
    private boolean isSecret;

    private String value;

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

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        String maskedDefaultValue = isSecret ? "<redacted>" : defaultValue;
        String maskedValue = isSecret ? "<redacted>" : value;

        return "McpVariable{" + "choices="
                + choices + ", defaultValue='"
                + maskedDefaultValue + '\'' + ", description='"
                + description + '\'' + ", format='"
                + format + '\'' + ", isRequired="
                + isRequired + ", isSecret="
                + isSecret + ", value='"
                + maskedValue + '\'' + '}';
    }
}
