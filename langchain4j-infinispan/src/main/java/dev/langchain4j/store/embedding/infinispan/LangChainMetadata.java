package dev.langchain4j.store.embedding.infinispan;

/**
 * Langchain Metadata item that is serialized for the langchain integration use case
 * @param name, the name of the metadata
 * @param value, the value of the metadata
 */
public record LangChainMetadata(String name, String value) {}

