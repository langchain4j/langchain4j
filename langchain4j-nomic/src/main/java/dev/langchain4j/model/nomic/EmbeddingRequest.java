package dev.langchain4j.model.nomic;

import java.util.List;

class EmbeddingRequest {

    private String model;
    private List<String> texts;
    private String taskType;

    EmbeddingRequest(String model, List<String> texts, String taskType) {
        this.model = model;
        this.texts = texts;
        this.taskType = taskType;
    }

    public static EmbeddingRequestBuilder builder() {
        return new EmbeddingRequestBuilder();
    }

    public String getModel() {
        return this.model;
    }

    public List<String> getTexts() {
        return this.texts;
    }

    public String getTaskType() {
        return this.taskType;
    }

    public static class EmbeddingRequestBuilder {
        private String model;
        private List<String> texts;
        private String taskType;

        EmbeddingRequestBuilder() {
        }

        public EmbeddingRequestBuilder model(String model) {
            this.model = model;
            return this;
        }

        public EmbeddingRequestBuilder texts(List<String> texts) {
            this.texts = texts;
            return this;
        }

        public EmbeddingRequestBuilder taskType(String taskType) {
            this.taskType = taskType;
            return this;
        }

        public EmbeddingRequest build() {
            return new EmbeddingRequest(this.model, this.texts, this.taskType);
        }

        public String toString() {
            return "EmbeddingRequest.EmbeddingRequestBuilder(model=" + this.model + ", texts=" + this.texts + ", taskType=" + this.taskType + ")";
        }
    }
}
