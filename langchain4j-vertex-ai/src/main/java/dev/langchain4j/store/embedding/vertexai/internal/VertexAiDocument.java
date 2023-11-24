package dev.langchain4j.store.embedding.vertexai.internal;

import com.google.gson.Gson;
import dev.langchain4j.data.segment.TextSegment;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * This class is used to serialize the document to JSON.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class VertexAiDocument {
    private static final Gson GSON = new Gson();

    private String memberOfIndex;
    private TextSegment segment;

    public String toJson() {
        return GSON.toJson(this);
    }

    public static VertexAiDocument fromJson(String json) {
        if (json == null) {
            return null;
        }

        return GSON.fromJson(json, VertexAiDocument.class);
    }
}
