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
     * Returns the {@link AgentsRegistry} discovered via {@link ServiceLoader}.
     * The result is lazily initialized on first access and cached for subsequent calls.
     * Call {@link #refresh()} to force re-discovery.
     *
     * @return the loaded registry, or an empty registry if no provider is available
     */
    static AgentsRegistry get() {
        return LazyHolder.INSTANCE;
    }

    /**
     * Forces re-discovery of the {@link AgentsRegistry} via {@link ServiceLoader}.
     * Subsequent calls to {@link #get()} will return the newly discovered registry.
     */
    static void refresh() {
        LazyHolder.INSTANCE = LazyHolder.discover();
    }

    class LazyHolder {
        private static AgentsRegistry INSTANCE = discover();

        private static AgentsRegistry discover() {
            ServiceLoader<AgentsRegistry> loader = ServiceLoader.load(AgentsRegistry.class);
            for (AgentsRegistry registry : loader) {
                return registry;
            }
            return new EmptyAgentsRegistry();
        }
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
