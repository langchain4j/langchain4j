package dev.langchain4j.agentic.workflow;

import java.util.List;
import java.util.function.Predicate;
import dev.langchain4j.agentic.cognisphere.Cognisphere;
import dev.langchain4j.agentic.internal.AgentExecutor;

public interface ConditionalAgentService<T> extends WorkflowService<ConditionalAgentService<T>, T> {

    ConditionalAgentService<T> subAgents(Predicate<Cognisphere> condition, Object... agents);

    ConditionalAgentService<T> subAgents(Predicate<Cognisphere> condition, List<AgentExecutor> agentExecutors);

    ConditionalAgentService<T> subAgent(Predicate<Cognisphere> condition, AgentExecutor agentExecutor);
}
