package dev.langchain4j.store.embedding.chroma;

import java.util.List;
import java.util.Map;

class SuccessfulResponse {

    private List<List<String>> ids;
    private List<List<Double>> distances;
    private List<List<Map<String, String>>> metadatas;
    private List<List<List<Float>>> embeddings;
    private List<List<String>> documents;

    public List<List<String>> getIds() {
        return ids;
    }

    public List<List<Double>> getDistances() {
        return distances;
    }

    public List<List<Map<String, String>>> getMetadatas() {
        return metadatas;
    }

    public List<List<List<Float>>> getEmbeddings() {
        return embeddings;
    }

    public List<List<String>> getDocuments() {
        return documents;
    }

    @Override
    public String toString() {
        return "SuccessfulResponse{" +
                "ids=" + ids +
                ", distances=" + distances +
                ", metadatas=" + metadatas +
                ", embeddings=" + embeddings +
                ", documents=" + documents +
                '}';
    }

}
