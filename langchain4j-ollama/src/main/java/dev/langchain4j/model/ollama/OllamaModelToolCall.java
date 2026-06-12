package dev.langchain4j.model.ollama;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
public class OllamaModelToolCall {

    private OllamaModelToolCallFunction function;

    OllamaModelToolCall() {}

    public OllamaModelToolCall(OllamaModelToolCallFunction function) {
        this.function = function;
    }

    public static Builder builder() {
        return new Builder();
    }

    public OllamaModelToolCallFunction getFunction() {
        return function;
    }

    public void setFunction(OllamaModelToolCallFunction function) {
        this.function = function;
    }

    public static class Builder {

        private OllamaModelToolCallFunction function;

        public Builder function(OllamaModelToolCallFunction function) {
            this.function = function;
            return this;
        }

        public OllamaModelToolCall build() {
            return new OllamaModelToolCall(function);
        }
    }
}
