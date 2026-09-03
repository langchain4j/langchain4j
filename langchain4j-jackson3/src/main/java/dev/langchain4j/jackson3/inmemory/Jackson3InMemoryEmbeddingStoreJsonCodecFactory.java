package dev.langchain4j.jackson3.inmemory;

import static dev.langchain4j.spi.PrioritizedFactory.YIELDS_TO_OTHERS;

import dev.langchain4j.spi.PrioritizedFactory;
import dev.langchain4j.spi.store.embedding.inmemory.InMemoryEmbeddingStoreJsonCodecFactory;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStoreJsonCodec;

public class Jackson3InMemoryEmbeddingStoreJsonCodecFactory implements InMemoryEmbeddingStoreJsonCodecFactory, PrioritizedFactory {

    @Override
    public int priority() {
        return YIELDS_TO_OTHERS; // a framework that supplies its own codec keeps it
    }


    @Override
    public InMemoryEmbeddingStoreJsonCodec create() {
        return new Jackson3InMemoryEmbeddingStoreJsonCodec();
    }
}
