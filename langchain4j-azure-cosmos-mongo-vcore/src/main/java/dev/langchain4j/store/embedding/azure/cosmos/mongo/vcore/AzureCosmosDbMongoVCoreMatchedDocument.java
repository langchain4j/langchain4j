package dev.langchain4j.store.embedding.azure.cosmos.mongo.vcore;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AzureCosmosDbMongoVCoreMatchedDocument {

    private String id;
    private List<Float> embedding;
    private String text;
    private Map<String, String> metadata;
    private Double score;
}
