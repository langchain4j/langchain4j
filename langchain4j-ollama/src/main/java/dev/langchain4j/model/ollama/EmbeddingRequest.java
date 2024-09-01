package dev.langchain4j.model.ollama;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
class EmbeddingRequest {

    private String model;
    private List<String> input;

    EmbeddingRequest() {
    }

    EmbeddingRequest(String model, List<String> input) {
        this.model = model;
        this.input = input;
    }

    static Builder builder() {
        return new Builder();
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public List<String> getInput() {
        return input;
    }

    public void setInput(List<String> input) {
        this.input = input;
    }

    static class Builder {

        private String model;
        private List<String> input;

        Builder model(String model) {
            this.model = model;
            return this;
        }

        Builder input(List<String> input) {
            this.input = input;
            return this;
        }

        EmbeddingRequest build() {
            return new EmbeddingRequest(model, input);
        }
    }
}
