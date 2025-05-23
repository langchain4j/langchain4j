package dev.langchain4j.agentic.supervisor;

import static dev.langchain4j.agentic.internal.AgentExecutor.agentsToExecutors;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import dev.langchain4j.agentic.internal.AgentInstance;
import dev.langchain4j.agentic.internal.AgentSpecification;
import dev.langchain4j.agentic.Cognisphere;
import dev.langchain4j.agentic.CognisphereOwner;
import dev.langchain4j.agentic.internal.AbstractAgentInvocationHandler;
import dev.langchain4j.agentic.internal.AgentExecutor;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;

public class SupervisorAgentService<T> {

    private final Class<T> agentServiceClass;

    private int maxAgentsInvocations = 5;
    private String outputName;

    private final PlannerAgent plannerAgent;
    private final ResponseAgent responseAgent;

    private final Map<String, AgentExecutor> agents = new HashMap<>();
    private String agentsList;

    private SupervisorAgentService(ChatModel chatModel, Class<T> agentServiceClass) {
        this.agentServiceClass = agentServiceClass;

        this.plannerAgent = AiServices.builder(PlannerAgent.class)
                .chatModel(chatModel)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(20))
                .build();

        this.responseAgent = AiServices.builder(ResponseAgent.class)
                .chatModel(chatModel)
                .build();
    }


    public T build() {
        return (T) Proxy.newProxyInstance(
                agentServiceClass.getClassLoader(),
                new Class<?>[] {agentServiceClass, AgentInstance.class, CognisphereOwner.class},
                new SupervisorInvocationHandler());
    }

    private class SupervisorInvocationHandler extends AbstractAgentInvocationHandler {

        public SupervisorInvocationHandler() {
            super(SupervisorAgentService.this.agentServiceClass, SupervisorAgentService.this.outputName);
        }

        public SupervisorInvocationHandler(Cognisphere cognisphere) {
            super(SupervisorAgentService.this.agentServiceClass, SupervisorAgentService.this.outputName, cognisphere);
        }

        @Override
        protected Object doAgentAction(Cognisphere cognisphere) {
            String request = (String) cognisphere.readState("request");
            String lastResponse = "";
            String memoryId = UUID.randomUUID().toString();

            for (int loopCount = 0; loopCount < maxAgentsInvocations; loopCount++) {

                AgentInvocation agentInvocation = plannerAgent.plan(memoryId, agentsList, request, lastResponse);
                System.out.println("*** Agent Invocation: " + agentInvocation);

                if (agentInvocation.getAgentName().equalsIgnoreCase("done")) {
                    String doneResponse = agentInvocation.getArguments().get("response");
                    if (doneResponse != null) {
                        ResponseScore score = responseAgent.scoreResponses(request, lastResponse, doneResponse);
                        System.out.println("*** " + score);
                        if (score.getScore2() > score.getScore1()) {
                            lastResponse = doneResponse;
                        }
                    }
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

                // Copy the chat memory of the invoked agent to the planner's chat memory
                ChatMemory plannerMemory = plannerAgent.getChatMemory(memoryId);
                cognisphere.lastInteractionMessages().forEach(plannerMemory::add);
            }

            plannerAgent.evictChatMemory(memoryId);
            if (outputName != null) {
                cognisphere.writeState(outputName, lastResponse);
            }
            return lastResponse;
        }

        @Override
        protected InvocationHandler createHandlerWithCognisphere(Cognisphere cognisphere) {
            return new SupervisorInvocationHandler(cognisphere);
        }
    }

    public static SupervisorAgentService<SupervisorAgent> builder(ChatModel chatModel) {
        return builder(chatModel, SupervisorAgent.class);
    }

    public static <T> SupervisorAgentService<T> builder(ChatModel chatModel, Class<T> agentServiceClass) {
        return new SupervisorAgentService<>(chatModel, agentServiceClass);
    }

    public SupervisorAgentService<T> subAgents(Object... agents) {
        List<AgentExecutor> agentExecutors = agentsToExecutors(Stream.of(agents).map(AgentInstance.class::cast).toList());
        for (AgentExecutor agentExecutor : agentExecutors) {
            if (!agentExecutor.agentSpecification().description().isEmpty()) {
                this.agents.put(agentExecutor.agentName(), agentExecutor);
            }
        }
        this.agentsList = this.agents.values()
                .stream()
                .map(AgentExecutor::agentSpecification)
                .map(AgentSpecification::toCard)
                .collect(Collectors.joining(", "));
        return this;
    }

    public SupervisorAgentService<T> outputName(String outputName) {
        this.outputName = outputName;
        return this;
    }

    public SupervisorAgentService<T> maxAgentsInvocations(int maxAgentsInvocations) {
        this.maxAgentsInvocations = maxAgentsInvocations;
        return this;
    }
}
