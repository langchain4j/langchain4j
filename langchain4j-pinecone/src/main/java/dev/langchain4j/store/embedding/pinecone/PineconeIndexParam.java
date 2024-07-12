package dev.langchain4j.store.embedding.pinecone;

import io.pinecone.clients.Pinecone;

public interface PineconeIndexParam {

    void createIndex(Pinecone pinecone, String index);
}
