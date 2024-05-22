package dev.langchain4j.model.jina;

import dev.langchain4j.data.embedding.Embedding;

public class JinaEmbedding {
    long index;
    float[] embedding;
    String object;

    public Embedding toEmbedding(){
        return Embedding.from(embedding);
    }
}
