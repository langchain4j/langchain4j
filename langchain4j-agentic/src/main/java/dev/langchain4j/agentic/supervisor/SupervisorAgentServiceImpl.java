package dev.langchain4j.agentic.supervisor;

import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.agentic.scope.AgenticScopeAccess;
import dev.langchain4j.agentic.internal.AbstractAgentInvocationHandler;
import dev.langchain4j.agentic.internal.AbstractService;
import dev.langchain4j.agentic.internal.AgentExecutor;
import dev.langchain4j.agentic.internal.AgentSpecification;
import dev.langchain4j.agentic.internal.AgentInvoker;
import dev.langchain4j.agentic.internal.AgenticScopeOwner;
import dev.langchain4j.agentic.internal.Context;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class SupervisorAgentServiceImpl<T> extends AbstractService<T, SupervisorAgentServiceImpl<T>> implements SupervisorAgentService<T> {

    private static final Logger LOG = LoggerFactory.getLogger(SupervisorAgentServiceImpl.class);

    private ChatModel chatModel;

    private int maxAgentsInvocations = 10;

    private ResponseAgent responseAgent;

    private final Map<String, AgentExecutor> agents = new HashMap<>();
    private String agentsList;

    private SupervisorContextStrategy contextStrategy = SupervisorContextStrategy.CHAT_MEMORY;
    private SupervisorResponseStrategy responseStrategy = SupervisorResponseStrategy.LAST;

    private Function<AgenticScope, String> requestGenerator;

    private SupervisorAgentServiceImpl(Class<T> agentServiceClass) {
        super(agentServiceClass);
    }

    public T build() {
        if (responseStrategy == SupervisorResponseStrategy.SCORED) {
            this.responseAgent = AiServices.builder(ResponseAgent.class)
                    .chatModel(chatModel)
                    .build();
        }
        return build(null);
    }

    T build(DefaultAgenticScope agenticScope) {
        return (T) Proxy.newProxyInstance(
                agentServiceClass.getClassLoader(),
                new Class<?>[] {agentServiceClass, AgentSpecification.class, AgenticScopeOwner.class, AgenticScopeAccess.class},
                new SupervisorInvocationHandler(buildPlannerAgent(agenticScope), agenticScope));
    }

    private PlannerAgent buildPlannerAgent(AgenticScope agenticScope) {
        if (agenticScope == null && isAgenticScopeDependent()) {
            return null;
        }
        var builder = AiServices.builder(PlannerAgent.class).chatModel(chatModel);
        switch (contextStrategy) {
            case CHAT_MEMORY:
                builder.chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(20));
                break;
            case SUMMARIZATION:
                builder.chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(2))
                        .chatRequestTransformer(new Context.Summarizer(agenticScope, chatModel));
                break;
            case CHAT_MEMORY_AND_SUMMARIZATION:
                builder.chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(20))
                        .chatRequestTransformer(new Context.Summarizer(agenticScope, chatModel));
                break;
        }
        return builder.build();
    }

    private boolean isAgenticScopeDependent() {
        return contextStrategy != SupervisorContextStrategy.CHAT_MEMORY;
    }

    private class SupervisorInvocationHandler extends AbstractAgentInvocationHandler {
        private final PlannerAgent plannerAgent;

        public SupervisorInvocationHandler(PlannerAgent plannerAgent, DefaultAgenticScope agenticScope) {
            super(SupervisorAgentServiceImpl.this, agenticScope);
            this.plannerAgent = plannerAgent;
        }

        @Override
        protected Object doAgentAction(DefaultAgenticScope agenticScope) {
            String request = requestGenerator != null ? requestGenerator.apply(agenticScope) : agenticScope.readState("request", "");
            String lastResponse = "";
            Object memoryId = agenticScope.memoryId();

            for (int loopCount = 0; loopCount < maxAgentsInvocations; loopCount++) {

                PlannerAgent planner = isAgenticScopeDependent() ?
                        agenticScope.getOrCreateAgent(agentId(), SupervisorAgentServiceImpl.this::buildPlannerAgent) :
                        this.plannerAgent;
                AgentInvocation agentInvocation = planner.plan(memoryId, agentsList, request, lastResponse);
                LOG.info("Agent Invocation: {}", agentInvocation);

                if (agentInvocation.getAgentName().equalsIgnoreCase("done")) {
                    String doneResponse = agentInvocation.getArguments().get("response");
                    lastResponse = response(request, lastResponse, doneResponse);
                    break;
                }

                String agentName = agentInvocation.getAgentName();
                AgentExecutor agentExec = agents.get(agentName);
                if (agentExec == null) {
                    throw new IllegalStateException("No agent found with name: " + agentName);
                }

                AgentInvoker agentSpec = agentExec.agentInvoker();
                if (agentSpec == null) {
                    throw new IllegalStateException("No specification found for agent: " + agentName);
                }

                agentInvocation.getArguments().forEach(agenticScope::writeState);
                lastResponse = agentExec.execute(agenticScope).toString();
            }

            if (outputName != null) {
                agenticScope.writeState(outputName, lastResponse);
            }
            return lastResponse;
        }

        private String response(String request, String lastResponse, String doneResponse) {
            if (doneResponse == null) {
                return lastResponse;
            }
            return switch (responseStrategy) {
                case LAST -> lastResponse;
                case SUMMARY -> doneResponse;
                case SCORED -> {
                    ResponseScore score = responseAgent.scoreResponses(request, lastResponse, doneResponse);
                    LOG.info("Response scores: {}", score);
                    yield score.getScore2() > score.getScore1() ? doneResponse : lastResponse;
                }
            };
        }

        @Override
        protected InvocationHandler createSubAgentWithAgenticScope(DefaultAgenticScope agenticScope) {
            return new SupervisorInvocationHandler(plannerAgent, agenticScope);
        }

        private String agentId() {
            return outputName + "@Supervisor";
        }
    }

    public static SupervisorAgentService<SupervisorAgent> builder() {
        return builder(SupervisorAgent.class);
    }

    public static <T> SupervisorAgentService<T> builder(Class<T> agentServiceClass) {
        return new SupervisorAgentServiceImpl<>(agentServiceClass);
    }

    @Override
    public SupervisorAgentServiceImpl<T> chatModel(ChatModel chatModel) {
        this.chatModel = chatModel;
        return this;
    }

    @Override
    public SupervisorAgentServiceImpl<T> requestGenerator(Function<AgenticScope, String> requestGenerator) {
        this.requestGenerator = requestGenerator;
        return this;
    }

    @Override
    public SupervisorAgentServiceImpl<T> contextGenerationStrategy(SupervisorContextStrategy contextStrategy) {
        this.contextStrategy = contextStrategy;
        return this;
    }

    @Override
    public SupervisorAgentServiceImpl<T> responseStrategy(SupervisorResponseStrategy responseStrategy) {
        this.responseStrategy = responseStrategy;
        return this;
    }

    @Override
    public SupervisorAgentServiceImpl<T> subAgents(List<AgentExecutor> agentExecutors) {
        for (AgentExecutor agentExecutor : agentExecutors) {
            if (!agentExecutor.agentInvoker().description().isEmpty()) {
                this.agents.put(agentExecutor.agentName(), agentExecutor);
            } else {
                throw new IllegalArgumentException("Agent '" + agentExecutor.agentName() +
                        "' must have a non-empty description in order to be used by the supervisor agent.");
            }
        }
        this.agentsList = this.agents.values()
                .stream()
                .map(AgentExecutor::agentInvoker)
                .map(AgentInvoker::toCard)
                .collect(Collectors.joining(", "));
        return this;
    }

    @Override
    public SupervisorAgentServiceImpl<T> maxAgentsInvocations(int maxAgentsInvocations) {
        this.maxAgentsInvocations = maxAgentsInvocations;
        return this;
    }
}
