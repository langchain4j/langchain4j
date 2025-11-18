package dev.langchain4j.store.embedding.chroma;

import dev.langchain4j.Internal;

@Internal
interface ChromaClient {
    Collection createCollection(CreateCollectionRequest createCollectionRequest);

    Collection collection(String collectionName);

    boolean addEmbeddings(String collectionId, AddEmbeddingsRequest addEmbeddingsRequest);

    QueryResponse queryCollection(String collectionId, QueryRequest queryRequest);

    void deleteEmbeddings(String collectionId, DeleteEmbeddingsRequest deleteEmbeddingsRequest);

    void deleteCollection(String collectionName);
}
