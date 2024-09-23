package dev.langchain4j.model.voyage;

import java.util.List;

class EmbeddingRequest {

    private List<String> input;
    private String model;
    private String inputType;
    private Boolean truncation;
    private String encodingFormat;

    EmbeddingRequest() {
    }

    EmbeddingRequest(List<String> input, String model, String inputType, Boolean truncation, String encodingFormat) {
        this.input = input;
        this.model = model;
        this.inputType = inputType;
        this.truncation = truncation;
        this.encodingFormat = encodingFormat;
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

    public Boolean getTruncation() {
        return truncation;
    }

    public String getEncodingFormat() {
        return encodingFormat;
    }

    static EmbeddingRequestBuilder builder() {
        return new EmbeddingRequestBuilder();
    }

    static class EmbeddingRequestBuilder {

        private List<String> input;
        private String model;
        private String inputType;
        private Boolean truncation;
        private String encodingFormat;

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

        EmbeddingRequestBuilder truncation(Boolean truncation) {
            this.truncation = truncation;
            return this;
        }

        EmbeddingRequestBuilder encodingFormat(String encodingFormat) {
            this.encodingFormat = encodingFormat;
            return this;
        }

        EmbeddingRequest build() {
            return new EmbeddingRequest(input, model, inputType, truncation, encodingFormat);
        }
    }
}
