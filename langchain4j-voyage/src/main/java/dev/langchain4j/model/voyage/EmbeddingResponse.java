package dev.langchain4j.model.voyage;

import java.util.List;

class EmbeddingResponse {

    private String object;
    private List<EmbeddingData> data;
    private String model;
    private TokenUsage usage;

    public String getObject() {
        return object;
    }

    public List<EmbeddingData> getData() {
        return data;
    }

    public String getModel() {
        return model;
    }

    public TokenUsage getUsage() {
        return usage;
    }

    static class EmbeddingData {

        private String object;
        private List<Float> embedding;
        private Integer index;

        public String getObject() {
            return object;
        }

        public List<Float> getEmbedding() {
            return embedding;
        }

        public Integer getIndex() {
            return index;
        }
    }
}
