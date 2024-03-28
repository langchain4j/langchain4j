package dev.langchain4j.store.embedding.infinispan;

import java.util.Set;

/**
 * Langchain item that is serialized for the langchain integration use case
 *
 * @param id, the id of the item
 * @param embedding, the vector
 * @param text, associated text
 * @param metadata, additional set of metadata
 */
public record LangChainInfinispanItem(String id, float[] embedding, String text, Set<LangChainMetadata> metadata) {}

