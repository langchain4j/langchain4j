package dev.langchain4j.agentic.planner;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.AgentSpecification;
import dev.langchain4j.agentic.MethodAgentSpecification;
import dev.langchain4j.agentic.AgentInstance;
import dev.langchain4j.agentic.DefaultChatState;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static dev.langchain4j.internal.Utils.getAnnotatedMethod;

public class AgentsSystem {

    private static final int MAX_NUMBER_OF_AGENT_INVOCATIONS = 5;

    private final PlannerAgent plannerAgent;
    private final ResponseAgent responseAgent;

    private final Map<String, AgentSpecification> agentsSpecs = new HashMap<>();
    private final Map<String, Object> agents = new HashMap<>();

    public AgentsSystem(ChatModel chatModel, Object... agents) {
        this.plannerAgent = AiServices.builder(PlannerAgent.class)
                .chatModel(chatModel)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(10))
                .build();

        this.responseAgent = AiServices.builder(ResponseAgent.class)
                .chatModel(chatModel)
                .build();

        for (Object agent : agents) {
            for (Method method : agent.getClass().getDeclaredMethods()) {
                getAnnotatedMethod(method, Agent.class).ifPresent(agentMethod -> processAgentMethod(agent, agentMethod) );
            }
        }
    }

    private void processAgentMethod(Object agent, Method agentMethod) {
        AgentSpecification agentSpecification = AgentSpecification.fromMethod(agentMethod);
        agentsSpecs.put(agentSpecification.name(), agentSpecification);
        agents.put(agentSpecification.name(), agent);
    }

    public String execute(String request) {
        String lastResponse = "";
        String memoryId = UUID.randomUUID().toString();
        String agentsList = agentsSpecs.values()
                .stream()
                .map(AgentSpecification::toCard)
                .collect(Collectors.joining(", "));

        Map<String, Object> stateMap = new HashMap<>();
        ChatMemory injectedChatMemory = new DefaultChatState(MessageWindowChatMemory.withMaxMessages(10), stateMap);

        for (int loopCount = 0; loopCount < MAX_NUMBER_OF_AGENT_INVOCATIONS; loopCount++) {

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
            AgentInstance agent = (AgentInstance) agents.get(agentName);
            if (agent == null) {
                throw new IllegalStateException("No agent found with name: " + agentName);
            }

            AgentSpecification agentSpec = agentsSpecs.get(agentName);
            if (agentSpec == null) {
                throw new IllegalStateException("No specification found for agent: " + agentName);
            }

            agent.setChatMemory(injectedChatMemory);
            try {
                lastResponse = agentSpec.method().invoke(agent, agentSpec.toInvocationArguments(agentInvocation.getArguments())).toString();

                // Copy the chat memory of the invoked agent to the planner's chat memory
                ChatMemory plannerMemory = plannerAgent.getChatMemory(memoryId);
                injectedChatMemory.messages().forEach(plannerMemory::add);
            } catch (IllegalAccessException  | InvocationTargetException e) {
                throw new RuntimeException(e);
            } finally {
                injectedChatMemory.clear();
            }
        }

        plannerAgent.evictChatMemory(memoryId);
        return lastResponse;
    }
}
