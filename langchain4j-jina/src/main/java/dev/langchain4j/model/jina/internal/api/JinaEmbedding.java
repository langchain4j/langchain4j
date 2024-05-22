package dev.langchain4j.model.jina.internal.api;

import dev.langchain4j.data.embedding.Embedding;

public class JinaEmbedding {

    public long index;
    public float[] embedding;
    public String object;

    public Embedding toEmbedding() {
        return Embedding.from(embedding);
    }
}
