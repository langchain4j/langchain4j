package dev.langchain4j.model.voyageai;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.List;

class ContextualizedEmbeddingResponse {

    private String object;
    private List<DocumentData> data;
    private String model;
    private TokenUsage usage;

    public String getObject() {
        return object;
    }

    public List<DocumentData> getData() {
        return data;
    }

    public String getModel() {
        return model;
    }

    public TokenUsage getUsage() {
        return usage;
    }

    static class DocumentData {

        private String object;

        @JsonDeserialize(using = VoyageAiEmbeddingDeserializer.class)
        private List<EmbeddingResponse.EmbeddingData> data;

        private Integer index;

        public String getObject() {
            return object;
        }

        public List<EmbeddingResponse.EmbeddingData> getData() {
            return data;
        }

        public Integer getIndex() {
            return index;
        }
    }
}
