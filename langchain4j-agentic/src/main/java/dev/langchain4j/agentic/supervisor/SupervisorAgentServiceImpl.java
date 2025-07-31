package dev.langchain4j.agentic.supervisor;

import dev.langchain4j.agentic.cognisphere.Cognisphere;
import dev.langchain4j.agentic.internal.AbstractAgentInvocationHandler;
import dev.langchain4j.agentic.internal.AbstractService;
import dev.langchain4j.agentic.internal.AgentExecutor;
import dev.langchain4j.agentic.internal.AgentInstance;
import dev.langchain4j.agentic.internal.AgentSpecification;
import dev.langchain4j.agentic.internal.CognisphereOwner;
import dev.langchain4j.agentic.internal.Context;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private PlannerAgent plannerAgent;
    private ResponseAgent responseAgent;

    private final Map<String, AgentExecutor> agents = new HashMap<>();
    private String agentsList;

    private SupervisorContextStrategy contextStrategy = SupervisorContextStrategy.CHAT_MEMORY;
    private SupervisorResponseStrategy responseStrategy = SupervisorResponseStrategy.SCORED;

    private Function<Cognisphere, String> requestGenerator;

    private SupervisorAgentServiceImpl(Class<T> agentServiceClass) {
        super(agentServiceClass);
    }

    public T build() {
        this.plannerAgent = buildPlannerAgent();
        if (responseStrategy == SupervisorResponseStrategy.SCORED) {
            this.responseAgent = AiServices.builder(ResponseAgent.class)
                    .chatModel(chatModel)
                    .build();
        }

        return (T) Proxy.newProxyInstance(
                agentServiceClass.getClassLoader(),
                new Class<?>[] {agentServiceClass, AgentInstance.class, CognisphereOwner.class},
                new SupervisorInvocationHandler());
    }

    private PlannerAgent buildPlannerAgent() {
        var builder = AiServices.builder(PlannerAgent.class).chatModel(chatModel);
        switch (contextStrategy) {
            case CHAT_MEMORY:
                builder.chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(20));
                break;
            case SUMMARIZATION:
                builder.chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(2))
                        .chatRequestTransformer(new Context.Summarizer(chatModel));
                break;
            case BOTH:
                builder.chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(20))
                        .chatRequestTransformer(new Context.Summarizer(chatModel));
                break;
        }
        return builder.build();
    }

    private class SupervisorInvocationHandler extends AbstractAgentInvocationHandler {

        public SupervisorInvocationHandler() {
            super(SupervisorAgentServiceImpl.this);
        }

        public SupervisorInvocationHandler(Cognisphere cognisphere) {
            super(SupervisorAgentServiceImpl.this, cognisphere);
        }

        @Override
        protected Object doAgentAction(Cognisphere cognisphere) {
            String request = requestGenerator != null ? requestGenerator.apply(cognisphere) : cognisphere.readState("request", "");
            String lastResponse = "";
            Object memoryId = cognisphere.id();

            for (int loopCount = 0; loopCount < maxAgentsInvocations; loopCount++) {

                AgentInvocation agentInvocation = plannerAgent.plan(memoryId, agentsList, request, lastResponse);
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

                AgentSpecification agentSpec = agentExec.agentSpecification();
                if (agentSpec == null) {
                    throw new IllegalStateException("No specification found for agent: " + agentName);
                }

                agentInvocation.getArguments().forEach(cognisphere::writeState);
                lastResponse = agentExec.invoke(cognisphere).toString();
            }

            if (outputName != null) {
                cognisphere.writeState(outputName, lastResponse);
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
        protected CognisphereOwner createSubAgentWithCognisphere(Cognisphere cognisphere) {
            return new SupervisorInvocationHandler(cognisphere);
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
    public SupervisorAgentServiceImpl<T> requestGenerator(Function<Cognisphere, String> requestGenerator) {
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
            if (!agentExecutor.agentSpecification().description().isEmpty()) {
                this.agents.put(agentExecutor.agentName(), agentExecutor);
            } else {
                throw new IllegalArgumentException("Agent '" + agentExecutor.agentName() +
                        "' must have a non-empty description in order to be used by the supervisor agent.");
            }
        }
        this.agentsList = this.agents.values()
                .stream()
                .map(AgentExecutor::agentSpecification)
                .map(AgentSpecification::toCard)
                .collect(Collectors.joining(", "));
        return this;
    }

    @Override
    public SupervisorAgentServiceImpl<T> maxAgentsInvocations(int maxAgentsInvocations) {
        this.maxAgentsInvocations = maxAgentsInvocations;
        return this;
    }
}
