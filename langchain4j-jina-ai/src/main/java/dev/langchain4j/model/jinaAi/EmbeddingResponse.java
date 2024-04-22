package dev.langchain4j.model.jinaAi;

import lombok.Data;

import java.util.List;
@Data
public class EmbeddingResponse {
    Usage usage;
    List<JinaAiEmbedding> data;
}
