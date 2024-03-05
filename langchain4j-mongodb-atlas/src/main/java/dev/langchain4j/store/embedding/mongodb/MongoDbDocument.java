package dev.langchain4j.store.embedding.mongodb;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.codecs.pojo.annotations.BsonId;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MongoDbDocument {

    @BsonId
    private String id;
    private List<Float> embedding;
    private String text;
    private Map<String, String> metadata;
}
