package dev.langchain4j.agentic.internal;

import dev.langchain4j.agentic.observability.AgentListener;

public interface McpClientBuilder<T> {

    McpClientBuilder<T> toolName(String toolName);

    McpClientBuilder<T> inputKeys(String... inputKeys);

    McpClientBuilder<T> outputKey(String outputKey);

    McpClientBuilder<T> async(boolean async);

    McpClientBuilder<T> listener(AgentListener agentListener);

    T build();
}
