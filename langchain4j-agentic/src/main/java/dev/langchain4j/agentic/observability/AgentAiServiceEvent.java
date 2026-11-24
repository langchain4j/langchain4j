package dev.langchain4j.agentic.observability;

import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.invocation.LangChain4jManaged;
import dev.langchain4j.observability.api.event.AiServiceEvent;
import java.util.Map;
import java.util.Objects;

/**
 * Notifies an event fired by the AI Service of an LLM-based agent, like a request sent to the model, the response
 * received from it or the execution of a tool, together with the agent that the AI Service belongs to.
 */
public final class AgentAiServiceEvent {

    private final AgentInstance agent;
    private final AiServiceEvent aiServiceEvent;

    /**
     * @param agent the agent whose AI Service fired the event
     * @param aiServiceEvent the event fired by the AI Service
     */
    public AgentAiServiceEvent(AgentInstance agent, AiServiceEvent aiServiceEvent) {
        this.agent = agent;
        this.aiServiceEvent = aiServiceEvent;
    }

    public AgentInstance agent() {
        return agent;
    }

    public AiServiceEvent aiServiceEvent() {
        return aiServiceEvent;
    }

    public AgenticScope agenticScope() {
        Map<Class<? extends LangChain4jManaged>, LangChain4jManaged> managedParameters =
                aiServiceEvent.invocationContext().managedParameters();
        return managedParameters == null ? null : (AgenticScope) managedParameters.get(AgenticScope.class);
    }

    public String agentName() {
        return agent.name();
    }

    public String agentId() {
        return agent.agentId();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof AgentAiServiceEvent that
                && Objects.equals(agent, that.agent)
                && Objects.equals(aiServiceEvent, that.aiServiceEvent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(agent, aiServiceEvent);
    }

    @Override
    public String toString() {
        return "AgentAiServiceEvent{agentName='" + (agent == null ? null : agent.name()) + "', eventClass="
                + (aiServiceEvent == null ? null : aiServiceEvent.eventClass().getSimpleName()) + "}";
    }
}
