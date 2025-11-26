package dev.langchain4j.agentic.internal;

import dev.langchain4j.agentic.agent.AgentRequest;
import dev.langchain4j.agentic.agent.AgentResponse;
import java.util.function.Consumer;

public interface A2AClientBuilder<T> {

    A2AClientBuilder<T> inputKeys(String... inputKeys);

    A2AClientBuilder<T> outputKey(String outputKey);

    A2AClientBuilder<T> async(boolean async);

    A2AClientBuilder<T> beforeAgentInvocation(Consumer<AgentRequest> invocationListener);

    A2AClientBuilder<T> afterAgentInvocation(Consumer<AgentResponse> completionListener);

    T build();
}
