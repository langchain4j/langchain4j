package dev.langchain4j.store.embedding.chroma;

import java.util.Map;

class Collection {

    private String id;
    private String name;
    private Map<String, Object> metadata;

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public Map<String, Object> metadata() {
        return metadata;
    }
}
