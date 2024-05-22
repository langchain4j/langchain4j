package dev.langchain4j.model.jina.internal.api;

import java.util.List;

public class EmbeddingResponse {

    public List<JinaEmbedding> data;
    public Usage usage;
}
