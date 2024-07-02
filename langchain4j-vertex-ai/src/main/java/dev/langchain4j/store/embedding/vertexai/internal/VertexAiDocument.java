package dev.langchain4j.store.embedding.vertexai.internal;

import com.google.gson.Gson;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * This class is used to serialize the document to JSON.
 */
@Getter
@NoArgsConstructor
public class VertexAiDocument {
    private static final Gson GSON = new Gson();

    private String memberOfIndex;
    private String content;
    private Map<String, String> metadata;

    public VertexAiDocument(String memberOfIndex, TextSegment textSegment) {
        this.memberOfIndex = memberOfIndex;
        if (textSegment != null) {
            this.content = textSegment.text();
            this.metadata = textSegment.metadata().asMap();
        } else {
            this.content = null;
            this.metadata = null;
        }
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    public TextSegment toTextSegment() {
        return new TextSegment(content, Metadata.from(metadata));
    }

    public static VertexAiDocument fromJson(String json) {
        if (json == null) {
            return null;
        }

        return GSON.fromJson(json, VertexAiDocument.class);
    }
}
