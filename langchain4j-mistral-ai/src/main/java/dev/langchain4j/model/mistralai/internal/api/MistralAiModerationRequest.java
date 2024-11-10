package dev.langchain4j.model.mistralai.internal.api;

import java.util.List;

public class MistralAiModerationRequest {

    private String model;
    private List<String> input;

    MistralAiModerationRequest() {}

    MistralAiModerationRequest(String model, List<String> input) {
        this.model = model;
        this.input = input;
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

    public static class Builder {
        private String model;
        private List<String> input;

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder input(List<String> input) {
            this.input = input;
            return this;
        }

        public MistralAiModerationRequest build() {
            return new MistralAiModerationRequest(model, input);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

}
