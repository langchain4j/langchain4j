package dev.langchain4j.store.embedding.opensearch;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * OpenSearch document object, for the purpose of construct document object from embedding and text segment
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
class Document {

    private float[] values;
    private String text;
    private Map<String, String> metadata;
    
}
