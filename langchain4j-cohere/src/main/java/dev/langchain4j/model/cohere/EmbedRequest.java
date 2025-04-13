package dev.langchain4j.model.cohere;

import java.util.List;

class EmbedRequest {

    private List<String> texts;
    private String model;
    private String inputType;

    EmbedRequest(List<String> texts, String model, String inputType) {
        this.texts = texts;
        this.model = model;
        this.inputType = inputType;
    }

    public static EmbedRequestBuilder builder() {
        return new EmbedRequestBuilder();
    }

    public List<String> getTexts() {
        return this.texts;
    }

    public String getModel() {
        return this.model;
    }

    public String getInputType() {
        return this.inputType;
    }

    public static class EmbedRequestBuilder {
        private List<String> texts;
        private String model;
        private String inputType;

        EmbedRequestBuilder() {
        }

        public EmbedRequestBuilder texts(List<String> texts) {
            this.texts = texts;
            return this;
        }

        public EmbedRequestBuilder model(String model) {
            this.model = model;
            return this;
        }

        public EmbedRequestBuilder inputType(String inputType) {
            this.inputType = inputType;
            return this;
        }

        public EmbedRequest build() {
            return new EmbedRequest(this.texts, this.model, this.inputType);
        }

        public String toString() {
            return "EmbedRequest.EmbedRequestBuilder(texts=" + this.texts + ", model=" + this.model + ", inputType=" + this.inputType + ")";
        }
    }
}
