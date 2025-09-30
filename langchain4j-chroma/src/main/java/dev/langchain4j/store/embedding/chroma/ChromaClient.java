package dev.langchain4j.store.embedding.chroma;

import dev.langchain4j.store.embedding.chroma.model.AddEmbeddingsRequest;
import dev.langchain4j.store.embedding.chroma.model.Collection;
import dev.langchain4j.store.embedding.chroma.model.CreateCollectionRequest;
import dev.langchain4j.store.embedding.chroma.model.DeleteEmbeddingsRequest;
import dev.langchain4j.store.embedding.chroma.model.QueryRequest;
import dev.langchain4j.store.embedding.chroma.model.QueryResponse;

public interface ChromaClient {
    Collection createCollection(CreateCollectionRequest createCollectionRequest);

    Collection collection(String collectionName);

    boolean addEmbeddings(String collectionId, AddEmbeddingsRequest addEmbeddingsRequest);

    QueryResponse queryCollection(String collectionId, QueryRequest queryRequest);

    void deleteEmbeddings(String collectionId, DeleteEmbeddingsRequest deleteEmbeddingsRequest);

    void deleteCollection(String collectionName);
}
