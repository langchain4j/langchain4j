package dev.langchain4j.jackson3.inmemory;

import dev.langchain4j.spi.store.embedding.inmemory.InMemoryEmbeddingStoreJsonCodecFactory;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStoreJsonCodec;

public class Jackson3InMemoryEmbeddingStoreJsonCodecFactory implements InMemoryEmbeddingStoreJsonCodecFactory {

    @Override
    public InMemoryEmbeddingStoreJsonCodec create() {
        return new Jackson3InMemoryEmbeddingStoreJsonCodec();
    }
}
