package dev.langchain4j.store.embedding.opensearch;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
class Document {

    private float[] vector;
    private String text;
    private Map<String, String> metadata;
}
