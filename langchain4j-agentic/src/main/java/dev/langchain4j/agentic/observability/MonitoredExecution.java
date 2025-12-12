package dev.langchain4j.agentic.observability;

import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.scope.AgenticScope;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a monitored execution of an agentic system, tracking the top-level agent invocation
 * and any other nested invocations, along with all the invocation currently in progress and
 * any errors that occur during execution.
 */
public class MonitoredExecution {

    private final AgentInvocation topLevelInvocations;

    private final Map<Object, AgentInvocation> ongoingInvocations = new ConcurrentHashMap<>();

    private AgentInvocationError agentInvocationError;

    MonitoredExecution(AgentRequest firstAgentRequest) {
        this.topLevelInvocations = new AgentInvocation(firstAgentRequest);
        ongoingInvocations.put(firstAgentRequest.agentId(), this.topLevelInvocations);
    }

    void beforeAgentInvocation(AgentRequest agentRequest) {
        AgentInvocation parentInvocation = parentInvocation(agentRequest.agent());
        AgentInvocation newInvocation = new AgentInvocation(agentRequest);
        parentInvocation.addNestedInvocation(newInvocation);
        ongoingInvocations.put(agentRequest.agentId(), newInvocation);
    }

    void afterAgentInvocation(AgentResponse agentResponse) {
        AgentInvocation finishedInvocation = ongoingInvocations.remove(agentResponse.agentId());
        if (finishedInvocation == null) {
            throw new IllegalStateException("No ongoing invocation found for agent ID: " + agentResponse.agentId());
        }
        finishedInvocation.finished(agentResponse);
    }

    void onAgentInvocationError(AgentInvocationError agentInvocationError) {
        this.agentInvocationError = agentInvocationError;
    }

    public Collection<AgentInvocation> ongoingInvocations() {
        return ongoingInvocations.values();
    }

    private AgentInvocation parentInvocation(AgentInstance agent) {
        return ongoingInvocations.values().stream()
                .filter(parent -> parent.agent().subagents().stream()
                        .anyMatch(subagent -> subagent.agentId().equals(agent.agentId())))
                .findFirst()
                .orElseThrow( () -> new IllegalStateException("No parent invocation found for agent ID: " + agent.agentId()));
    }

    public boolean done() {
        return topLevelInvocations.done();
    }

    public boolean hasError() {
        return agentInvocationError != null;
    }

    public AgentInvocationError error() {
        return agentInvocationError;
    }

    public AgentInvocation topLevelInvocations() {
        return topLevelInvocations;
    }

    public AgenticScope agenticScope() {
        return topLevelInvocations.agenticScope();
    }

    @Override
    public String toString() {
        return topLevelInvocations.toString();
    }
}
