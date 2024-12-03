package dev.langchain4j.model.voyageai;

import java.util.List;

class RerankResponse {

    private String object;
    private List<RerankData> data;
    private String model;
    private TokenUsage usage;

    public String getObject() {
        return object;
    }

    public List<RerankData> getData() {
        return data;
    }

    public String getModel() {
        return model;
    }

    public TokenUsage getUsage() {
        return usage;
    }

    static class RerankData {

        private String object;
        private Double relevanceScore;
        private Integer index;

        public String getObject() {
            return object;
        }

        public Double getRelevanceScore() {
            return relevanceScore;
        }

        public Integer getIndex() {
            return index;
        }
    }
}
