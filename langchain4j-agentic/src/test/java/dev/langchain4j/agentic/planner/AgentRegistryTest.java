package dev.langchain4j.agentic.planner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.langchain4j.agentic.scope.AgenticScope;
import java.util.Collection;
import org.junit.jupiter.api.Test;

class AgentRegistryTest {

    @Test
    void shouldReturnEmptyListInitially() {
        InMemoryAgentRegistry registry = new InMemoryAgentRegistry();

        Collection<AgentInstance> agents = registry.discoverAgents(null);

        assertThat(agents).isEmpty();
    }

    @Test
    void shouldRegisterAndDiscoverAgent() {
        InMemoryAgentRegistry registry = new InMemoryAgentRegistry();
        AgentInstance agent = mockAgent("agent1");

        registry.register(agent);

        assertThat(registry.discoverAgents(null)).containsExactly(agent);
    }

    @Test
    void shouldRegisterMultipleAgents() {
        InMemoryAgentRegistry registry = new InMemoryAgentRegistry();
        AgentInstance agent1 = mockAgent("agent1");
        AgentInstance agent2 = mockAgent("agent2");
        AgentInstance agent3 = mockAgent("agent3");

        registry.register(agent1);
        registry.register(agent2);
        registry.register(agent3);

        assertThat(registry.discoverAgents(null)).containsExactlyInAnyOrder(agent1, agent2, agent3);
    }

    @Test
    void shouldUnregisterAgentByName() {
        InMemoryAgentRegistry registry = new InMemoryAgentRegistry();
        AgentInstance agent1 = mockAgent("agent1");
        AgentInstance agent2 = mockAgent("agent2");
        registry.register(agent1);
        registry.register(agent2);

        registry.unregister("agent1");

        assertThat(registry.discoverAgents(null)).containsExactly(agent2);
    }

    @Test
    void shouldIgnoreUnregisterOfUnknownName() {
        InMemoryAgentRegistry registry = new InMemoryAgentRegistry();
        AgentInstance agent = mockAgent("agent1");
        registry.register(agent);

        registry.unregister("nonexistent");

        assertThat(registry.discoverAgents(null)).containsExactly(agent);
    }

    @Test
    void shouldReplaceAgentWhenRegisteredWithSameId() {
        InMemoryAgentRegistry registry = new InMemoryAgentRegistry();
        AgentInstance original = mockAgent("agent1");
        AgentInstance replacement = mockAgent("agent1");

        registry.register(original);
        registry.register(replacement);

        Collection<AgentInstance> discovered = registry.discoverAgents(null);
        assertThat(discovered).hasSize(1);
        assertThat(discovered.iterator().next()).isSameAs(replacement);
    }

    @Test
    void shouldIgnoreScopeParameter() {
        InMemoryAgentRegistry registry = new InMemoryAgentRegistry();
        AgentInstance agent = mockAgent("agent1");
        registry.register(agent);
        AgenticScope scope = mock(AgenticScope.class);

        assertThat(registry.discoverAgents(scope)).containsExactly(agent);
        assertThat(registry.discoverAgents(null)).containsExactly(agent);
    }

    private static AgentInstance mockAgent(String agentId) {
        AgentInstance agent = mock(AgentInstance.class);
        when(agent.agentId()).thenReturn(agentId);
        return agent;
    }
}
