package dev.langchain4j.spi.store.embedding.inmemory;

import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStoreJsonCodec;

public interface InMemoryEmbeddingStoreJsonCodecFactory {

    InMemoryEmbeddingStoreJsonCodec create();
}
