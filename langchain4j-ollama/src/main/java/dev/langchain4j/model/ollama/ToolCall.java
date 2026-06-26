package dev.langchain4j.model.ollama;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
class ToolCall {

    private String id;
    private FunctionCall function;

    ToolCall() {}

    ToolCall(String id, FunctionCall function) {
        this.id = id;
        this.function = function;
    }

    static Builder builder() {
        return new Builder();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public FunctionCall getFunction() {
        return function;
    }

    public void setFunction(FunctionCall function) {
        this.function = function;
    }

    static class Builder {

        private String id;
        private FunctionCall function;

        Builder id(String id) {
            this.id = id;
            return this;
        }

        Builder function(FunctionCall function) {
            this.function = function;
            return this;
        }

        ToolCall build() {
            return new ToolCall(id, function);
        }
    }
}
