package dev.langchain4j.spi.store.embedding.inmemory;

import dev.langchain4j.Internal;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStoreJsonCodec;

@Internal
public interface InMemoryEmbeddingStoreJsonCodecFactory {

    InMemoryEmbeddingStoreJsonCodec create();
}
