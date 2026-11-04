package dev.langchain4j.store.memory.chat.infinispan;

/**
 * Protobuf entity representing a tool execution request stored in Infinispan.
 *
 * @param id        the tool execution request id
 * @param name      the tool name
 * @param arguments the tool arguments (typically JSON)
 */
public record LangChainToolExecutionRequest(String id, String name, String arguments) {}
