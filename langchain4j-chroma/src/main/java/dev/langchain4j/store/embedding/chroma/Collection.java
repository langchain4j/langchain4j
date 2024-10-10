package dev.langchain4j.store.embedding.chroma;

import java.util.Map;

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
