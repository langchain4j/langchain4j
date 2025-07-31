package dev.langchain4j.agentic.workflow;

import java.util.List;
import java.util.function.Predicate;
import dev.langchain4j.agentic.cognisphere.Cognisphere;
import dev.langchain4j.agentic.internal.AgentExecutor;

public interface ConditionialAgentService<T> extends WorkflowService<ConditionialAgentService<T>, T> {

    ConditionialAgentService<T> subAgents(Predicate<Cognisphere> condition, Object... agents);

    ConditionialAgentService<T> subAgents(Predicate<Cognisphere> condition, List<AgentExecutor> agentExecutors);

    ConditionialAgentService<T> subAgent(Predicate<Cognisphere> condition, AgentExecutor agentExecutor);
}
