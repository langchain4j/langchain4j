package dev.langchain4j.model.ollama;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.Map;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
class FunctionCall {

    private Integer index;
    private String name;
    private Map<String, Object> arguments;

    FunctionCall() {
    }

    FunctionCall(Integer index, String name, Map<String, Object> arguments) {
        this.index = index;
        this.name = name;
        this.arguments = arguments;
    }

    static Builder builder() {
        return new Builder();
    }

    public Integer getIndex() {
        return index;
    }

    public void setIndex(Integer index) {
        this.index = index;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<String, Object> getArguments() {
        return arguments;
    }

    public void setArguments(Map<String, Object> arguments) {
        this.arguments = arguments;
    }

    static class Builder {

        private Integer index;
        private String name;
        private Map<String, Object> arguments;

        Builder index(Integer index) {
            this.index = index;
            return this;
        }

        Builder name(String name) {
            this.name = name;
            return this;
        }

        Builder arguments(Map<String, Object> arguments) {
            this.arguments = arguments;
            return this;
        }

        FunctionCall build() {
            return new FunctionCall(index, name, arguments);
        }
    }
}
