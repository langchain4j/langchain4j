package dev.langchain4j.store.embedding.mongodb.document;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmbeddingMatchDocument {
    private String id;
    private List<Double> embedding;
    private String text;
    private Map<String, String> metadata;
    private Double score;
}
