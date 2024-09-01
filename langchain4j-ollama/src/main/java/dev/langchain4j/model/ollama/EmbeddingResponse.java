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
class EmbeddingResponse {

    private String model;
    private List<float[]> embeddings;

    EmbeddingResponse() {
    }

    EmbeddingResponse(String model, List<float[]> embeddings) {
        this.model = model;
        this.embeddings = embeddings;
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

    public List<float[]> getEmbeddings() {
        return embeddings;
    }

    public void setEmbeddings(List<float[]> embeddings) {
        this.embeddings = embeddings;
    }

    static class Builder {

        private String model;
        private List<float[]> embeddings;

        Builder model(String model) {
            this.model = model;
            return this;
        }

        Builder embeddings(List<float[]> embeddings) {
            this.embeddings = embeddings;
            return this;
        }

        EmbeddingResponse build() {
            return new EmbeddingResponse(model, embeddings);
        }
    }
}
