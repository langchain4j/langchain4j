package dev.langchain4j.agentic.planner;

import dev.langchain4j.agentic.scope.AgenticScope;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

public class InMemoryAgentRegistry implements AgentRegistry {

    private record ConditionalAgent(AgentInstance agent, Predicate<AgenticScope> condition) {}

    private final ConcurrentHashMap<String, ConditionalAgent> agents = new ConcurrentHashMap<>();

    public void register(Object agent) {
        register(agent, null);
    }

    public void register(Object agent, Predicate<AgenticScope> condition) {
        AgentInstance agentInstance = (AgentInstance) agent;
        agents.put(agentInstance.agentId(), new ConditionalAgent(agentInstance, condition));
    }

    public void unregister(String agentId) {
        agents.remove(agentId);
    }

    @Override
    public Collection<AgentInstance> discoverAgents(AgenticScope scope) {
        return agents.values().stream()
                .filter(e -> e.condition() == null || e.condition().test(scope))
                .map(ConditionalAgent::agent)
                .toList();
    }
}
