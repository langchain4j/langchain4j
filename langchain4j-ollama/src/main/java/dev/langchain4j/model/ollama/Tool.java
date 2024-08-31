package dev.langchain4j.model.ollama;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
class Tool {

    private String type;
    private Function function;

    Tool() {
    }

    Tool(String type, Function function) {
        this.type = type;
        this.function = function;
    }

    static Builder builder() {
        return new Builder();
    }

    String getType() {
        return type;
    }

    void setType(String type) {
        this.type = type;
    }

    Function getFunction() {
        return function;
    }

    void setFunction(Function function) {
        this.function = function;
    }

    static class Builder {

        private final String type = "function";
        private Function function;

        Builder function(Function function) {
            this.function = function;
            return this;
        }

        Tool build() {
            return new Tool(type, function);
        }
    }
}
