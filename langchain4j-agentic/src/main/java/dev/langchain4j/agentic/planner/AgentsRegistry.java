package dev.langchain4j.agentic.planner;

import dev.langchain4j.agentic.internal.AgentExecutor;
import java.util.Collection;
import java.util.List;
import java.util.ServiceLoader;

/**
 * SPI for dynamically discovering and registering agents at runtime.
 */
public interface AgentsRegistry {

    Collection<AgentExecutor> discoverAgents();

    static AgentsRegistry get() {
        return Provider.agentsRegistry;
    }

    class Provider {

        static AgentsRegistry agentsRegistry = loadAgentsRegistry();

        private Provider() {}

        private static AgentsRegistry loadAgentsRegistry() {
            ServiceLoader<AgentsRegistry> loader = ServiceLoader.load(AgentsRegistry.class);

            for (AgentsRegistry registry : loader) {
                return registry;
            }
            return new NoOpAgentsRegistry();
        }
    }

    class NoOpAgentsRegistry implements AgentsRegistry {

        private NoOpAgentsRegistry() {}

        @Override
        public Collection<AgentExecutor> discoverAgents() {
            return List.of();
        }
    }
}
