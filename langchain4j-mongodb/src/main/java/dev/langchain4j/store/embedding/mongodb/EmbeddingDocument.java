package dev.langchain4j.store.embedding.mongodb;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.bson.codecs.pojo.annotations.BsonId;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@RequiredArgsConstructor
@SuperBuilder
class EmbeddingDocument {
    @BsonId
    @NonNull
    private String id;
    @NonNull
    private List<Double> embedding;

    private String text;
    private Map<String, String> metadata;
}
