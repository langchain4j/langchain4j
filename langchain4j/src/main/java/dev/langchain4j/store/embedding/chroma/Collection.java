package dev.langchain4j.store.embedding.chroma;

import java.util.Map;

class Collection {

    private String name;
    private String id;
    private Map<String, String> metadata;

    public String name() {
        return name;
    }

    public String id() {
        return id;
    }

    public Map<String, String> metadata() {
        return metadata;
    }

    @Override
    public String toString() {
        return "Collection{" +
                "name='" + name + '\'' +
                ", id='" + id + '\'' +
                ", chromaMetadata=" + metadata +
                '}';
    }

}
