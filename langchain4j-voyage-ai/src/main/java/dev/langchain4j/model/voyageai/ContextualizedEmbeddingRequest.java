package dev.langchain4j.model.voyageai;

import java.util.List;

class ContextualizedEmbeddingRequest {

    private List<List<String>> inputs;
    private String model;
    private String inputType;
    private Boolean truncation;
    private String encodingFormat;

    ContextualizedEmbeddingRequest() {}

    ContextualizedEmbeddingRequest(
            List<List<String>> inputs, String model, String inputType, Boolean truncation, String encodingFormat) {
        this.inputs = inputs;
        this.model = model;
        this.inputType = inputType;
        this.truncation = truncation;
        this.encodingFormat = encodingFormat;
    }

    public List<List<String>> getInputs() {
        return inputs;
    }

    public String getModel() {
        return model;
    }

    public String getInputType() {
        return inputType;
    }

    public Boolean getTruncation() {
        return truncation;
    }

    public String getEncodingFormat() {
        return encodingFormat;
    }

    static Builder builder() {
        return new Builder();
    }

    static class Builder {

        private List<List<String>> inputs;
        private String model;
        private String inputType;
        private Boolean truncation;
        private String encodingFormat;

        Builder inputs(List<List<String>> inputs) {
            this.inputs = inputs;
            return this;
        }

        Builder model(String model) {
            this.model = model;
            return this;
        }

        Builder inputType(String inputType) {
            this.inputType = inputType;
            return this;
        }

        Builder truncation(Boolean truncation) {
            this.truncation = truncation;
            return this;
        }

        Builder encodingFormat(String encodingFormat) {
            this.encodingFormat = encodingFormat;
            return this;
        }

        ContextualizedEmbeddingRequest build() {
            return new ContextualizedEmbeddingRequest(inputs, model, inputType, truncation, encodingFormat);
        }
    }
}
