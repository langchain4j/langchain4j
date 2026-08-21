package dev.langchain4j.json.jackson3;

import dev.langchain4j.spi.store.embedding.inmemory.InMemoryEmbeddingStoreJsonCodecFactory;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStoreJsonCodec;

public class Jackson3InMemoryEmbeddingStoreJsonCodecFactory implements InMemoryEmbeddingStoreJsonCodecFactory {

    @Override
    public InMemoryEmbeddingStoreJsonCodec create() {
        return new Jackson3InMemoryEmbeddingStoreJsonCodec();
    }
}
