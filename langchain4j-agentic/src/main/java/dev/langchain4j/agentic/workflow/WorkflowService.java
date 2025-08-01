package dev.langchain4j.agentic.workflow;

import dev.langchain4j.agentic.cognisphere.Cognisphere;
import dev.langchain4j.agentic.internal.AgentExecutor;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public interface WorkflowService<T, W> {

    W build();

    T subAgents(Object... agents);

    T subAgents(List<AgentExecutor> agentExecutors);

    T beforeCall(Consumer<Cognisphere> beforeCall);

    T outputName(String outputName);

    T output(Function<Cognisphere, Object> output);
}
