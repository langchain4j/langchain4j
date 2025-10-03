package dev.langchain4j.store.embedding.chroma;

import dev.langchain4j.Internal;
import java.util.Map;

@Internal
class Collection {

    private String id;
    private String name;
    private Map<String, Object> metadata;

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }
}
