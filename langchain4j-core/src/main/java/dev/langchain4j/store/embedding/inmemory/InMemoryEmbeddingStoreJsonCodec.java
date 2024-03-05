package dev.langchain4j.store.embedding.inmemory;

import dev.langchain4j.data.segment.TextSegment;

public interface InMemoryEmbeddingStoreJsonCodec {
    InMemoryEmbeddingStore<TextSegment> fromJson(String json);

    String toJson(InMemoryEmbeddingStore<?> store);
}
