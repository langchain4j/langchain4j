package dev.langchain4j.store.embedding.chroma;

import dev.langchain4j.Internal;
import java.util.List;
import java.util.Map;

@Internal
class QueryResponse {

    private List<List<String>> ids;
    private List<List<List<Float>>> embeddings;
    private List<List<String>> documents;
    private List<List<Map<String, Object>>> metadatas;
    private List<List<Double>> distances;

    public List<List<String>> getIds() {
        return ids;
    }

    public List<List<List<Float>>> getEmbeddings() {
        return embeddings;
    }

    public List<List<String>> getDocuments() {
        return documents;
    }

    public List<List<Map<String, Object>>> getMetadatas() {
        return metadatas;
    }

    public List<List<Double>> getDistances() {
        return distances;
    }
}
