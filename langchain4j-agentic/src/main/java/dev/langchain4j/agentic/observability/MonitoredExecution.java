package dev.langchain4j.agentic.observability;

import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.scope.AgenticScope;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MonitoredExecution {

    private final AgentCall topLevelCall;

    private final Map<Object, AgentCall> ongoingCalls = new ConcurrentHashMap<>();

    private AgentInvocationError agentInvocationError;

    MonitoredExecution(AgentRequest firstAgentRequest) {
        this.topLevelCall = new AgentCall(firstAgentRequest);
        ongoingCalls.put(firstAgentRequest.agentId(), this.topLevelCall);
    }

    void beforeAgentInvocation(AgentRequest agentRequest) {
        AgentCall parentCall = parentCall(agentRequest.agent());
        AgentCall newCall = new AgentCall(agentRequest);
        parentCall.addNestedCall(newCall);
        ongoingCalls.put(agentRequest.agentId(), newCall);
    }

    void afterAgentInvocation(AgentResponse agentResponse) {
        AgentCall finishedCall = ongoingCalls.remove(agentResponse.agentId());
        if (finishedCall == null) {
            throw new IllegalStateException("No ongoing call found for agent ID: " + agentResponse.agentId());
        }
        finishedCall.finished(agentResponse);
    }

    void onAgentInvocationError(AgentInvocationError agentInvocationError) {
        this.agentInvocationError = agentInvocationError;
    }

    public Collection<AgentCall> ongoingCalls() {
        return ongoingCalls.values();
    }

    private AgentCall parentCall(AgentInstance agent) {
        return ongoingCalls.values().stream()
                .filter(parent -> parent.agent().subagents().stream()
                        .anyMatch(subagent -> subagent.agentId().equals(agent.agentId())))
                .findFirst()
                .orElseThrow( () -> new IllegalStateException("No parent call found for agent ID: " + agent.agentId()));
    }

    public boolean done() {
        return topLevelCall.done();
    }

    public boolean hasError() {
        return agentInvocationError != null;
    }

    public AgentInvocationError error() {
        return agentInvocationError;
    }

    public AgentCall topLevelCall() {
        return topLevelCall;
    }

    public AgenticScope agenticScope() {
        return topLevelCall.agenticScope();
    }

    @Override
    public String toString() {
        return topLevelCall.toString();
    }
}
