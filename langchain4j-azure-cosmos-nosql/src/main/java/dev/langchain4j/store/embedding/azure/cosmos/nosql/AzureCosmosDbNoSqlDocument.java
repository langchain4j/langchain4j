package dev.langchain4j.store.embedding.azure.cosmos.nosql;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
class AzureCosmosDbNoSqlDocument {
    private String id;
    private List<Float> embedding;
    private String text;
    private Map<String, String> metadata;
}
