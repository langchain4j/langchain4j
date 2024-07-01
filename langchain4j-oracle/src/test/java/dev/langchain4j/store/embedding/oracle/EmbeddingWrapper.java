package dev.langchain4j.store.embedding.oracle;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

@Getter
public class EmbeddingWrapper {
    private static final EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();
    private String content;
    private Embedding embedding;
    private TextSegment textSegment;
    private Map<String, Object> map = new HashMap<>();

    public static EmbeddingWrapper of(String content) {
        EmbeddingWrapper wrapper = new EmbeddingWrapper();
        return wrapper.content(content);
    }

    public EmbeddingWrapper content(String content) {
        this.content = content;
        this.embedding = embeddingModel.embed(content).content();
        this.textSegment = new TextSegment(content, new Metadata());
        return this;
    }

    public EmbeddingWrapper kv(String key, Object value) {
        map.put(key, value);
        textSegment = new TextSegment(content, new Metadata(map));
        return this;
    }
}
