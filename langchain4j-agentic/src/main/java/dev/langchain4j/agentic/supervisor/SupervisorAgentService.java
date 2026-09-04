package dev.langchain4j.agentic.supervisor;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agentic.agent.ErrorContext;
import dev.langchain4j.agentic.agent.ErrorRecoveryResult;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.tool.ToolArgumentsErrorHandler;
import dev.langchain4j.service.tool.ToolExecutionErrorHandler;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;

public interface SupervisorAgentService<T> {

    T build();

    SupervisorAgentService<T> chatModel(ChatModel chatModel);

    SupervisorAgentService<T> chatMemoryProvider(ChatMemoryProvider chatMemoryProvider);

    SupervisorAgentService<T> name(String name);

    SupervisorAgentService<T> description(String description);

    SupervisorAgentService<T> outputKey(String outputKey);

    SupervisorAgentService<T> requestGenerator(Function<AgenticScope, String> requestGenerator);

    SupervisorAgentService<T> contextGenerationStrategy(SupervisorContextStrategy contextStrategy);

    SupervisorAgentService<T> responseStrategy(SupervisorResponseStrategy responseStrategy);

    SupervisorAgentService<T> supervisorContext(String supervisorContext);

    SupervisorAgentService<T> subAgents(Object... agents);

    SupervisorAgentService<T> subAgents(Collection<?> agents);

    SupervisorAgentService<T> maxAgentsInvocations(int maxAgentsInvocations);

    SupervisorAgentService<T> output(Function<AgenticScope, Object> output);

    SupervisorAgentService<T> errorHandler(Function<ErrorContext, ErrorRecoveryResult> errorHandler);

    SupervisorAgentService<T> listener(AgentListener agentListener);

    SupervisorAgentService<T> beforeCall(Consumer<AgenticScope> beforeCall);

    SupervisorAgentService<T> compensateOnError(boolean compensateOnError);

    SupervisorAgentService<T> tools(Object... objectsWithTools);

    SupervisorAgentService<T> tools(Map<ToolSpecification, ToolExecutor> toolsMap);

    SupervisorAgentService<T> tools(
            Map<ToolSpecification, ToolExecutor> toolsMap, Set<String> immediateReturnToolNames);

    SupervisorAgentService<T> toolProvider(ToolProvider toolProvider);

    SupervisorAgentService<T> toolProviders(Collection<ToolProvider> toolProviders);

    SupervisorAgentService<T> toolProviders(ToolProvider... toolProviders);

    SupervisorAgentService<T> maxToolCallingRoundTrips(int maxToolCallingRoundTrips);

    SupervisorAgentService<T> hallucinatedToolNameStrategy(
            Function<ToolExecutionRequest, ToolExecutionResultMessage> hallucinatedToolNameStrategy);

    SupervisorAgentService<T> executeToolsConcurrently();

    SupervisorAgentService<T> executeToolsConcurrently(Executor executor);

    SupervisorAgentService<T> toolArgumentsErrorHandler(ToolArgumentsErrorHandler toolArgumentsErrorHandler);

    SupervisorAgentService<T> toolExecutionErrorHandler(ToolExecutionErrorHandler toolExecutionErrorHandler);
}
