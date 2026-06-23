package dev.langchain4j.agentic.supervisor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgentRegistry;
import dev.langchain4j.agentic.planner.InMemoryAgentRegistry;
import dev.langchain4j.agentic.planner.InitPlanningContext;
import dev.langchain4j.agentic.planner.PlanningContext;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SupervisorPlannerRegistryTest {

    AgenticScope agenticScope;

    @BeforeEach
    void setUp() {
        agenticScope = DefaultAgenticScope.ephemeralAgenticScope();
        agenticScope.writeState("request", "test request");
    }

    @Test
    void shouldMergeRegistryAgentsWithSubagentsOnInit() {
        AgentInstance subagent = stubAgent("sub1", "A sub agent");
        AgentInstance registryAgent = stubAgent("reg1", "A registry agent");

        InMemoryAgentRegistry registry = new InMemoryAgentRegistry();
        registry.register(registryAgent);

        AtomicReference<String> capturedSystemMessage = new AtomicReference<>();
        ChatModel chatModel = capturingSystemMessage(capturedSystemMessage);

        SupervisorPlanner planner = createPlanner(chatModel);
        planner.init(initContext(List.of(subagent), registry));

        planner.nextAction(planningContext());

        assertThat(capturedSystemMessage.get()).contains("sub1").contains("reg1");
    }

    @Test
    void shouldPreferSubagentsOverRegistryAgents() {
        AgentInstance subagent = stubAgent("shared-id", "Sub version");
        AgentInstance registryAgent = stubAgent("shared-id", "Registry version");

        InMemoryAgentRegistry registry = new InMemoryAgentRegistry();
        registry.register(registryAgent);

        AtomicReference<String> capturedSystemMessage = new AtomicReference<>();
        ChatModel chatModel = capturingSystemMessage(capturedSystemMessage);

        SupervisorPlanner planner = createPlanner(chatModel);
        planner.init(initContext(List.of(subagent), registry));

        planner.nextAction(planningContext());

        assertThat(capturedSystemMessage.get()).contains("Sub version");
        assertThat(capturedSystemMessage.get()).doesNotContain("Registry version");
    }

    @Test
    void shouldRediscoverAgentsOnEachIteration() {
        AgentInstance subagent = stubAgent("sub1", "A sub agent");

        List<AgentInstance> dynamicAgents = new ArrayList<>();
        AgentRegistry dynamicRegistry = scope -> new ArrayList<>(dynamicAgents);

        AtomicReference<String> capturedSystemMessage = new AtomicReference<>();
        ChatModel chatModel = capturingSystemMessage(capturedSystemMessage);

        SupervisorPlanner planner = createPlanner(chatModel);
        planner.init(initContext(List.of(subagent), dynamicRegistry));

        planner.nextAction(planningContext());
        String firstSystemMessage = capturedSystemMessage.get();

        AgentInstance lateAgent = stubAgent("late1", "A late-arriving agent");
        dynamicAgents.add(lateAgent);

        planner.nextAction(planningContext());
        String secondSystemMessage = capturedSystemMessage.get();

        assertThat(firstSystemMessage).doesNotContain("late1");
        assertThat(secondSystemMessage).contains("late1");
    }

    @Test
    void shouldWorkWithNullRegistry() {
        AgentInstance subagent = stubAgent("sub1", "A sub agent");

        ChatModel chatModel = doneImmediately();
        SupervisorPlanner planner = createPlanner(chatModel);
        planner.init(initContext(List.of(subagent), null));

        planner.nextAction(planningContext());
    }

    @Test
    void shouldWorkWithEmptyRegistry() {
        AgentInstance subagent = stubAgent("sub1", "A sub agent");

        InMemoryAgentRegistry registry = new InMemoryAgentRegistry();

        ChatModel chatModel = doneImmediately();
        SupervisorPlanner planner = createPlanner(chatModel);
        planner.init(initContext(List.of(subagent), registry));

        planner.nextAction(planningContext());
    }

    @Test
    void shouldPassScopeToRegistry() {
        AgentInstance subagent = stubAgent("sub1", "A sub agent");

        AtomicReference<AgenticScope> capturedScope = new AtomicReference<>();
        AgentRegistry capturingRegistry = scope -> {
            capturedScope.set(scope);
            return List.of();
        };

        ChatModel chatModel = doneImmediately();
        SupervisorPlanner planner = createPlanner(chatModel);
        planner.init(initContext(List.of(subagent), capturingRegistry));

        planner.nextAction(planningContext());

        assertThat(capturedScope.get()).isSameAs(agenticScope);
    }

    @Test
    void shouldThrowWhenAgentNotFoundAnywhere() {
        AgentInstance subagent = stubAgent("sub1", "A sub agent");

        ChatModel chatModel = chatModelInvokingAgent("nonexistent");
        SupervisorPlanner planner = createPlanner(chatModel);
        planner.init(initContext(List.of(subagent), null));

        assertThatThrownBy(() -> planner.nextAction(planningContext()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("nonexistent");
    }

    private InitPlanningContext initContext(List<AgentInstance> subagents, AgentRegistry registry) {
        AgentInstance plannerAgent = stubAgent("supervisor", "Supervisor");
        return new InitPlanningContext(agenticScope, plannerAgent, subagents, registry);
    }

    private PlanningContext planningContext() {
        return new PlanningContext(agenticScope, null);
    }

    private SupervisorPlanner createPlanner(ChatModel chatModel) {
        return new SupervisorPlanner(
                chatModel, null, 10,
                SupervisorContextStrategy.CHAT_MEMORY,
                SupervisorResponseStrategy.LAST,
                null, "output", null);
    }

    private static AgentInstance stubAgent(String agentId, String description) {
        AgentInstance agent = mock(AgentInstance.class);
        when(agent.agentId()).thenReturn(agentId);
        when(agent.name()).thenReturn(agentId);
        when(agent.description()).thenReturn(description);
        when(agent.arguments()).thenReturn(List.of());
        return agent;
    }

    private static ChatModel capturingSystemMessage(AtomicReference<String> capture) {
        return new ChatModel() {
            @Override
            public ChatResponse chat(ChatRequest request) {
                for (ChatMessage msg : request.messages()) {
                    if (msg instanceof SystemMessage sm) {
                        capture.set(sm.text());
                    }
                }
                return doneResponse();
            }
        };
    }

    private static ChatModel doneImmediately() {
        return new ChatModel() {
            @Override
            public ChatResponse chat(ChatRequest request) {
                return doneResponse();
            }
        };
    }

    private static ChatModel chatModelInvokingAgent(String agentName) {
        return new ChatModel() {
            @Override
            public ChatResponse chat(ChatRequest request) {
                return ChatResponse.builder()
                        .aiMessage(AiMessage.from("{\"agentName\": \"" + agentName + "\", \"arguments\": {}}"))
                        .build();
            }
        };
    }

    private static ChatResponse doneResponse() {
        return ChatResponse.builder()
                .aiMessage(AiMessage.from("{\"agentName\": \"done\", \"arguments\": {}}"))
                .build();
    }
}
