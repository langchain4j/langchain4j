package dev.langchain4j.store.embedding.mongodb.document;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TextSegmentDocument {
    private String text;
    private Map<String, String> metadata;
}
