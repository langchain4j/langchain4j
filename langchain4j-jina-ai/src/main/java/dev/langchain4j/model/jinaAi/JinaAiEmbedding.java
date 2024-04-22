package dev.langchain4j.model.jinaAi;

import dev.langchain4j.data.embedding.Embedding;

import java.util.List;

public class JinaAiEmbedding {
    long index;
    float[] embedding;
    String object;

    public Embedding toEmbedding(){
        return Embedding.from(embedding);
    }
}
