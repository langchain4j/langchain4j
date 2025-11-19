package dev.langchain4j.agentic.workflow;

import java.util.List;
import java.util.function.Predicate;
import dev.langchain4j.agentic.planner.AgenticService;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.internal.AgentExecutor;

public interface ConditionalAgentService<T> extends AgenticService<ConditionalAgentService<T>, T> {

    ConditionalAgentService<T> subAgents(Predicate<AgenticScope> condition, Object... agents);

    ConditionalAgentService<T> subAgents(Predicate<AgenticScope> condition, List<AgentExecutor> agentExecutors);

    ConditionalAgentService<T> subAgent(Predicate<AgenticScope> condition, AgentExecutor agentExecutor);
}
