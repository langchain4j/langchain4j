package dev.langchain4j.model.ollama;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;
import java.util.Map;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
class Parameters {

    private String type;
    private Map<String, Map<String, Object>> properties;
    private List<String> required;

    Parameters() {
    }

    Parameters(String type, Map<String, Map<String, Object>> properties, List<String> required) {
        this.type = type;
        this.properties = properties;
        this.required = required;
    }

    static Builder builder() {
        return new Builder();
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Map<String, Map<String, Object>> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, Map<String, Object>> properties) {
        this.properties = properties;
    }

    public List<String> getRequired() {
        return required;
    }

    public void setRequired(List<String> required) {
        this.required = required;
    }

    static class Builder {

        private String type = "object";
        private Map<String, Map<String, Object>> properties;
        private List<String> required;

        Builder type(String type) {
            this.type = type;
            return this;
        }

        Builder properties(Map<String, Map<String, Object>> properties) {
            this.properties = properties;
            return this;
        }

        Builder required(List<String> required) {
            this.required = required;
            return this;
        }

        Parameters build() {
            return new Parameters(type, properties, required);
        }
    }
}
