package dev.langchain4j.store.embedding.chroma;

import java.util.List;
import java.util.Map;

class QueryResponse {

    private List<List<String>> ids;
    private List<List<Double>> distances;
    private List<List<Map<String, String>>> metadatas;
    private List<List<List<Float>>> embeddings;
    private List<List<String>> documents;

    public List<List<String>> ids() {
        return ids;
    }

    public List<List<Double>> distances() {
        return distances;
    }

    public List<List<Map<String, String>>> metadatas() {
        return metadatas;
    }

    public List<List<List<Float>>> embeddings() {
        return embeddings;
    }

    public List<List<String>> documents() {
        return documents;
    }

}
