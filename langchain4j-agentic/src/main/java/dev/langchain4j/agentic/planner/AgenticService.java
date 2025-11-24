package dev.langchain4j.agentic.planner;

import dev.langchain4j.agentic.agent.AgentRequest;
import dev.langchain4j.agentic.agent.AgentResponse;
import dev.langchain4j.agentic.agent.ErrorContext;
import dev.langchain4j.agentic.agent.ErrorRecoveryResult;
import dev.langchain4j.agentic.declarative.AgentState;
import dev.langchain4j.agentic.internal.AgentExecutor;
import dev.langchain4j.agentic.scope.AgenticScope;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public interface AgenticService<T, A> {

    A build();

    T subAgents(Object... agents);

    T subAgents(List<AgentExecutor> agentExecutors);

    T beforeCall(Consumer<AgenticScope> beforeCall);

    T name(String name);

    T description(String description);

    T outputKey(String outputKey);
    T outputKey(Class<? extends AgentState<?>> outputKey);

    T output(Function<AgenticScope, Object> output);

    T errorHandler(Function<ErrorContext, ErrorRecoveryResult> errorHandler);

    T beforeAgentInvocation(Consumer<AgentRequest> invocationListener);

    T afterAgentInvocation(Consumer<AgentResponse> completionListener);
}
