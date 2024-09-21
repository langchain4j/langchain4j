package dev.langchain4j.model.voyage;

import java.util.List;

class EmbeddingRequest {

    private List<String> input;
    private String model;
    private String inputType;
    private boolean truncation;
    private String encodeFormat;

    EmbeddingRequest() {
    }

    public EmbeddingRequest(List<String> input, String model, String inputType, boolean truncation, String encodeFormat) {
        this.input = input;
        this.model = model;
        this.inputType = inputType;
        this.truncation = truncation;
        this.encodeFormat = encodeFormat;
    }

    public List<String> getInput() {
        return input;
    }

    public String getModel() {
        return model;
    }

    public String getInputType() {
        return inputType;
    }

    public boolean getTruncation() {
        return truncation;
    }

    public String getEncodeFormat() {
        return encodeFormat;
    }

    static EmbeddingRequestBuilder builder() {
        return new EmbeddingRequestBuilder();
    }

    static class EmbeddingRequestBuilder {

        private List<String> input;
        private String model;
        private String inputType;
        private boolean truncation;
        private String encodeFormat;

        EmbeddingRequestBuilder input(List<String> input) {
            this.input = input;
            return this;
        }

        EmbeddingRequestBuilder model(String model) {
            this.model = model;
            return this;
        }

        EmbeddingRequestBuilder inputType(String inputType) {
            this.inputType = inputType;
            return this;
        }

        EmbeddingRequestBuilder truncation(boolean truncation) {
            this.truncation = truncation;
            return this;
        }

        EmbeddingRequestBuilder encodeFormat(String encodeFormat) {
            this.encodeFormat = encodeFormat;
            return this;
        }

        EmbeddingRequest build() {
            return new EmbeddingRequest(input, model, inputType, truncation, encodeFormat);
        }
    }
}
