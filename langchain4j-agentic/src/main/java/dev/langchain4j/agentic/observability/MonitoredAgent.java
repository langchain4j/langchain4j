package dev.langchain4j.agentic.observability;

/**
 * Interface that agent service interfaces can extend to automatically register
 * an {@link AgentMonitor} as a listener. The monitor is created during agent building
 * and can be retrieved via {@link #agentMonitor()}.
 *
 * <p>Example usage:
 * <pre>{@code
 * public interface MyAgent extends MonitoredAgent {
 *
 *     @Agent("Performs some task")
 *     String run(String input);
 * }
 *
 * MyAgent agent = new AgentBuilder<>(MyAgent.class)
 *         .chatModel(model)
 *         .build();
 *
 * AgentMonitor monitor = agent.agentMonitor();
 * }</pre>
 */
public interface MonitoredAgent {

    /**
     * Returns the {@link AgentMonitor} automatically registered for this agent.
     */
    AgentMonitor agentMonitor();
}