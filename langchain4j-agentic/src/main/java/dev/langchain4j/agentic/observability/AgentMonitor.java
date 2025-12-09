package dev.langchain4j.agentic.observability;

import dev.langchain4j.agentic.scope.AgenticScope;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AgentMonitor implements AgentListener {

    private final Map<Object, List<MonitoredExecution>> successfulExecutions = new ConcurrentHashMap<>();
    private final Map<Object, List<MonitoredExecution>> failedExecutions = new ConcurrentHashMap<>();
    private final Map<Object, MonitoredExecution> ongoingExecutions = new ConcurrentHashMap<>();

    @Override
    public void beforeAgentInvocation(AgentRequest agentRequest) {
        Object memoryId = agentRequest.agenticScope().memoryId();
        MonitoredExecution currentExecution = ongoingExecutions.get(memoryId);
        if (currentExecution == null) {
            currentExecution = new MonitoredExecution(agentRequest);
            ongoingExecutions.put(memoryId, currentExecution);
        } else {
            currentExecution.beforeAgentInvocation(agentRequest);
        }
    }

    @Override
    public void afterAgentInvocation(AgentResponse agentResponse) {
        Object memoryId = agentResponse.agenticScope().memoryId();
        MonitoredExecution execution = ongoingExecutions.get(memoryId);
        execution.afterAgentInvocation(agentResponse);
        if (execution.done()) {
            ongoingExecutions.remove(memoryId);
            successfulExecutions.computeIfAbsent(memoryId, k -> new ArrayList<>()).add(execution);
        }
    }

    @Override
    public void onAgentInvocationError(AgentInvocationError agentInvocationError) {
        Object memoryId = agentInvocationError.agenticScope().memoryId();
        MonitoredExecution execution = ongoingExecutions.remove(memoryId);
        if (execution != null) {
            execution.onAgentInvocationError(agentInvocationError);
            failedExecutions.computeIfAbsent(memoryId, k -> new ArrayList<>()).add(execution);
        }
    }

    public Map<Object, MonitoredExecution> ongoingExecutions() {
        return ongoingExecutions;
    }

    public MonitoredExecution ongoingExecutionFor(AgenticScope agenticScope) {
        return ongoingExecutionFor(agenticScope.memoryId());
    }

    public MonitoredExecution ongoingExecutionFor(Object memoryId) {
        return ongoingExecutions.get(memoryId);
    }

    public List<MonitoredExecution> successfulExecutions() {
        return successfulExecutions.values().stream().flatMap(List::stream).toList();
    }

    public List<MonitoredExecution> successfulExecutionsFor(AgenticScope agenticScope) {
        return successfulExecutionsFor(agenticScope.memoryId());
    }

    public List<MonitoredExecution> successfulExecutionsFor(Object memoryId) {
        return successfulExecutions.getOrDefault(memoryId, List.of());
    }

    public List<MonitoredExecution> failedExecutions() {
        return failedExecutions.values().stream().flatMap(List::stream).toList();
    }

    public List<MonitoredExecution> failedExecutionsFor(AgenticScope agenticScope) {
        return failedExecutionsFor(agenticScope.memoryId());
    }

    public List<MonitoredExecution> failedExecutionsFor(Object memoryId) {
        return failedExecutions.getOrDefault(memoryId, List.of());
    }
}
