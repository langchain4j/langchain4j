package dev.langchain4j.model.ollama;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
class ToolCall {

    private FunctionCall function;

    ToolCall() {
    }

    ToolCall(FunctionCall function) {
        this.function = function;
    }

    static Builder builder() {
        return new Builder();
    }

    public FunctionCall getFunction() {
        return function;
    }

    public void setFunction(FunctionCall function) {
        this.function = function;
    }

    static class Builder {

        private FunctionCall function;

        Builder function(FunctionCall function) {
            this.function = function;
            return this;
        }

        ToolCall build() {
            return new ToolCall(function);
        }
    }
}
