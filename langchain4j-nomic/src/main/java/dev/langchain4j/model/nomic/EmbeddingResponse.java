package dev.langchain4j.model.nomic;

import java.util.List;

class EmbeddingResponse {

    private List<float[]> embeddings;
    private Usage usage;

    public List<float[]> getEmbeddings() {
        return this.embeddings;
    }

    public Usage getUsage() {
        return this.usage;
    }
}
