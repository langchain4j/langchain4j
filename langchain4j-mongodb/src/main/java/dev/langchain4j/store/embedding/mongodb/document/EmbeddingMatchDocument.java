package dev.langchain4j.store.embedding.mongodb.document;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.codecs.pojo.annotations.BsonProperty;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmbeddingMatchDocument {
    private String id;
    private List<Double> embedding;
    private TextSegmentDocument embedded;
    @BsonProperty("vectorSearchScore")
    private Double score;
}
