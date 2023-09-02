package dev.langchain4j.store.embedding.elasticsearch;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Elasticsearch document object, for the purpose of construct document object from embedding and text segment
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Document {

    private float[] vector;
    private String text;
    private Map<String, String> metadata;
}
