package dev.langchain4j.model.zhipu.chat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.*;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;


@JsonInclude(NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public final class Parameters {

    private String type;
    private Map<String, Map<String, Object>> properties;
    private List<String> required;

    private Parameters(Builder builder) {
        this.type = "object";
        this.properties = builder.properties;
        this.required = builder.required;
    }

    public static Builder builder() {
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

    public static final class Builder {
        private Map<String, Map<String, Object>> properties;
        private List<String> required;

        private Builder() {
            this.properties = new HashMap<>();
            this.required = new ArrayList<>();
        }

        public Builder properties(Map<String, Map<String, Object>> properties) {
            if (properties != null) {
                this.properties = Collections.unmodifiableMap(properties);
            }

            return this;
        }

        public Builder required(List<String> required) {
            if (required != null) {
                this.required = Collections.unmodifiableList(required);
            }

            return this;
        }

        public Parameters build() {
            return new Parameters(this);
        }
    }
}