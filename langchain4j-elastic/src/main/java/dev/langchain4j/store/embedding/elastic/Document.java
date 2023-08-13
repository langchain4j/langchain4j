package dev.langchain4j.store.embedding.elastic;

import dev.langchain4j.data.document.Metadata;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Elasticsearch document object, for the purpose of construct document object from embedding and text segment
 */
@Data
@Builder
public class Document {

    private List<Float> vector;
    private String text;
    private Metadata metadata;

    public Document(List<Float> vector, String text, Metadata metadata) {
        this.vector = vector;
        this.text = text;
        this.metadata = metadata;
    }
}
