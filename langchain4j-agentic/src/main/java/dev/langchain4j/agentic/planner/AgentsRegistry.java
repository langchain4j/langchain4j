package dev.langchain4j.agentic.planner;

import java.util.Map;
import java.util.ServiceLoader;

/**
 * SPI for discovering and providing {@link AgentInstance}s by name or type.
 *
 * <p>Implementations are loaded via {@link ServiceLoader}. At most one provider
 * is supported; if none is found, an empty registry that throws on every lookup
 * is returned.
 *
 * <p>Registry-provided agents can be mixed with locally defined agents in any
 * agentic pattern (sequence, supervisor, planner, etc.).
 */
public interface AgentsRegistry {

    /**
     * Returns all agents registered in this registry, keyed by agent name.
     *
     * @return an unmodifiable map of agent names to {@link AgentInstance}s
     */
    Map<String, AgentInstance> allAgents();

    /**
     * Returns the agent registered with the given name.
     *
     * @param name the agent name
     * @return the matching {@link AgentInstance}
     * @throws RuntimeException if no agent with the given name is found
     */
    AgentInstance getAgent(String name);

    /**
     * Returns the agent matching the given type.
     *
     * @param agentType the agent class to look up
     * @param <T> the agent type
     * @return the matching agent, cast to {@code T}
     * @throws RuntimeException if no agent of the given type is found
     */
    <T> T getAgent(Class<T> agentType);

    /**
     * Loads the {@link AgentsRegistry} via {@link ServiceLoader}.
     * Returns the first provider found, or an empty registry if none is available.
     *
     * @return the loaded registry
     */
    static AgentsRegistry get() {
        ServiceLoader<AgentsRegistry> loader = ServiceLoader.load(AgentsRegistry.class);
        for (AgentsRegistry registry : loader) {
            return registry;
        }
        return new EmptyAgentsRegistry();
    }

    class EmptyAgentsRegistry implements AgentsRegistry {

        private EmptyAgentsRegistry() {}

        @Override
        public Map<String, AgentInstance> allAgents() {
            return Map.of();
        }

        @Override
        public AgentInstance getAgent(String name) {
            throw new RuntimeException("No agent found with name: " + name);
        }

        @Override
        public <T> T getAgent(Class<T> agentType) {
            throw new RuntimeException("No agent found with type: " + agentType.getName());
        }
    }
}
