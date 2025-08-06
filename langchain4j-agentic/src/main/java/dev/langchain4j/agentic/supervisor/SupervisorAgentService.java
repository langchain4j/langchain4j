package dev.langchain4j.agentic.supervisor;

import java.util.List;
import java.util.function.Function;
import dev.langchain4j.agentic.agent.ErrorContext;
import dev.langchain4j.agentic.agent.ErrorRecoveryResult;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.internal.AgentExecutor;
import dev.langchain4j.model.chat.ChatModel;

public interface SupervisorAgentService<T> {

    T build();

    SupervisorAgentService<T> chatModel(ChatModel chatModel);

    SupervisorAgentService<T> outputName(String outputName);

    SupervisorAgentService<T> requestGenerator(Function<AgenticScope, String> requestGenerator);

    SupervisorAgentService<T> contextGenerationStrategy(SupervisorContextStrategy contextStrategy);

    SupervisorAgentService<T> responseStrategy(SupervisorResponseStrategy responseStrategy);

    SupervisorAgentService<T> subAgents(Object... agents);

    SupervisorAgentService<T> subAgents(List<AgentExecutor> agentExecutors);

    SupervisorAgentService<T> maxAgentsInvocations(int maxAgentsInvocations);

    SupervisorAgentService<T> errorHandler(Function<ErrorContext, ErrorRecoveryResult> errorHandler);
}
