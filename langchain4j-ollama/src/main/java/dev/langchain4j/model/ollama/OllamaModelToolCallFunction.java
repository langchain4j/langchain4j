package dev.langchain4j.model.ollama;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
public class OllamaModelToolCallFunction {

    private Integer index;
    private String name;
    private Map<String, Object> arguments;

    OllamaModelToolCallFunction() {}

    public OllamaModelToolCallFunction(Integer index, String name, Map<String, Object> arguments) {
        this.index = index;
        this.name = name;
        this.arguments = arguments;
    }

    public static Builder builder() {
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

    public static class Builder {

        private Integer index;
        private String name;
        private Map<String, Object> arguments;

        public Builder index(Integer index) {
            this.index = index;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder arguments(Map<String, Object> arguments) {
            this.arguments = arguments;
            return this;
        }

        public OllamaModelToolCallFunction build() {
            return new OllamaModelToolCallFunction(index, name, arguments);
        }
    }
}
