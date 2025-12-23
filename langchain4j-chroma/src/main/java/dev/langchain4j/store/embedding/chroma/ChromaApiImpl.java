package dev.langchain4j.store.embedding.chroma;

import dev.langchain4j.Internal;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Internal
class ChromaApiImpl {

    private final ChromaHttpClient httpClient;

    public ChromaApiImpl(ChromaHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public Collection collection(String collectionName) throws IOException {
        Map<String, String> pathParams = new HashMap<>();
        pathParams.put("collection_name", collectionName);
        return httpClient.get("api/v1/collections/{collection_name}", Collection.class, pathParams);
    }

    public Collection createCollection(CreateCollectionRequest createCollectionRequest) throws IOException {
        return httpClient.post("api/v1/collections", createCollectionRequest, Collection.class);
    }

    public boolean addEmbeddings(String collectionId, AddEmbeddingsRequest embedding) throws IOException {
        Map<String, String> pathParams = new HashMap<>();
        pathParams.put("collection_id", collectionId);
        Boolean result =
                httpClient.post("api/v1/collections/{collection_id}/add", embedding, Boolean.class, pathParams);
        return Boolean.TRUE.equals(result);
    }

    public QueryResponse queryCollection(String collectionId, QueryRequest queryRequest) throws IOException {
        Map<String, String> pathParams = new HashMap<>();
        pathParams.put("collection_id", collectionId);
        return httpClient.post(
                "api/v1/collections/{collection_id}/query", queryRequest, QueryResponse.class, pathParams);
    }

    @SuppressWarnings("unchecked")
    public List<String> deleteEmbeddings(String collectionId, DeleteEmbeddingsRequest embedding) throws IOException {
        Map<String, String> pathParams = new HashMap<>();
        pathParams.put("collection_id", collectionId);
        return httpClient.post("api/v1/collections/{collection_id}/delete", embedding, List.class, pathParams);
    }

    public Collection deleteCollection(String collectionName) throws IOException {
        Map<String, String> pathParams = new HashMap<>();
        pathParams.put("collection_name", collectionName);
        httpClient.delete("api/v1/collections/{collection_name}", pathParams);
        return null; // DELETE operations typically don't return a body
    }
}
