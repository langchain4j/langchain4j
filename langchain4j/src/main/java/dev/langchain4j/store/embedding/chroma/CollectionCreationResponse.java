package dev.langchain4j.store.embedding.chroma;

import java.util.Map;

public class CollectionCreationResponse {

    private String name;
    private String id;
    private Map<String, String> metadata;

    public String getName() {
        return name;
    }

    public String getId() {
        return id;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    @Override
    public String toString() {
        return "CollectionCreationResponse{" +
                "name='" + name + '\'' +
                ", id='" + id + '\'' +
                ", chromaMetadata=" + metadata +
                '}';
    }

}
