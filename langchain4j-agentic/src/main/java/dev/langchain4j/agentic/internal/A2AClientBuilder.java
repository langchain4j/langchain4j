package dev.langchain4j.agentic.internal;

import dev.langchain4j.agentic.observability.AgentListener;

public interface A2AClientBuilder<T> {

    A2AClientBuilder<T> inputKeys(String... inputKeys);

    A2AClientBuilder<T> outputKey(String outputKey);

    A2AClientBuilder<T> async(boolean async);

    A2AClientBuilder<T> listener(AgentListener agentListener);

    T build();
}
