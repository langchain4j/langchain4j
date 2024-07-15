package dev.langchain4j.store.embedding.chroma;

import java.util.List;
import java.util.Map;

class QueryResponse {

    private List<List<String>> ids;
    private List<List<List<Float>>> embeddings;
    private List<List<String>> documents;
    private List<List<Map<String, Object>>> metadatas;
    private List<List<Double>> distances;

    public List<List<String>> ids() {
        return ids;
    }

    public List<List<List<Float>>> embeddings() {
        return embeddings;
    }

    public List<List<String>> documents() {
        return documents;
    }

    public List<List<Map<String, Object>>> metadatas() {
        return metadatas;
    }

    public List<List<Double>> distances() {
        return distances;
    }
}
