package dev.langchain4j.agentic.workflow;

import dev.langchain4j.agentic.agent.ErrorContext;
import dev.langchain4j.agentic.agent.ErrorRecoveryResult;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.internal.AgentExecutor;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public interface WorkflowService<T, W> {

    W build();

    T subAgents(Object... agents);

    T subAgents(List<AgentExecutor> agentExecutors);

    T beforeCall(Consumer<AgenticScope> beforeCall);

    T outputName(String outputName);

    T output(Function<AgenticScope, Object> output);

    T errorHandler(Function<ErrorContext, ErrorRecoveryResult> errorHandler);
}
