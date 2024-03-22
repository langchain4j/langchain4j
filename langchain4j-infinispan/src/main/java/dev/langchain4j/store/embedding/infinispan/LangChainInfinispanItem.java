package dev.langchain4j.store.embedding.infinispan;

import java.util.Set;

public record LangChainInfinispanItem(String id, float[] embedding, String text, Set<LangChainMetadata> metadata) {}

