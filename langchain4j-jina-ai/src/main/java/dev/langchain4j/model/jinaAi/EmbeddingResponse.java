package dev.langchain4j.model.jinaAi;

import dev.langchain4j.data.embedding.Embedding;
import lombok.Data;

import java.util.List;
@Data
public class EmbeddingResponse {
    Usage usage;
    List<JinaAiEmbedding> embeddingList;
}
