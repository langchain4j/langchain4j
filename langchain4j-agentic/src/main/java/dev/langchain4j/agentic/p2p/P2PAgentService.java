package dev.langchain4j.agentic.p2p;

import dev.langchain4j.agentic.agent.ErrorContext;
import dev.langchain4j.agentic.agent.ErrorRecoveryResult;
import dev.langchain4j.agentic.scope.AgenticScope;
import java.util.concurrent.Executor;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;

public interface P2PAgentService<T> {

    T build();

    P2PAgentService<T> name(String outputName);

    P2PAgentService<T> description(String outputName);

    P2PAgentService<T> outputName(String outputName);

    P2PAgentService<T> subAgents(Object... agents);

    P2PAgentService<T> maxAgentsInvocations(int maxAgentsInvocations);

    P2PAgentService<T> output(Function<AgenticScope, Object> output);

    P2PAgentService<T> errorHandler(Function<ErrorContext, ErrorRecoveryResult> errorHandler);

    P2PAgentService<T> executor(Executor executor);

    P2PAgentService<T> exitCondition(Predicate<AgenticScope> exitCondition);
    P2PAgentService<T> exitCondition(BiPredicate<AgenticScope, Integer> exitCondition);
}
