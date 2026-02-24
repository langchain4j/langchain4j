package dev.langchain4j.agentic.observability;

import dev.langchain4j.Internal;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.scope.AgenticScope;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Monitors agent executions and provides observability for the LangChain4j Agentic system.
 * Generates a self-contained HTML report visualizing the static topology of an agentic system
 * and the dynamic execution traces.
 *
 * <p>The report includes:
 * <ul>
 *   <li>A visual tree chart of the agent hierarchy showing topology types, names, and properties</li>
 *   <li>A waterfall timeline of execution traces grouped by memory/session ID</li>
 * </ul>
 */
public class AgentMonitor implements AgentListener {

    private AgentInstance rootAgent;

    private final Map<Object, List<MonitoredExecution>> successfulExecutions = new ConcurrentHashMap<>();
    private final Map<Object, List<MonitoredExecution>> failedExecutions = new ConcurrentHashMap<>();
    private final Map<Object, MonitoredExecution> ongoingExecutions = new ConcurrentHashMap<>();

    @Internal
    public void setRootAgent(AgentInstance rootAgent) {
        this.rootAgent = rootAgent;
    }

    AgentInstance rootAgent() {
        return rootAgent;
    }

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

    @Override
    public boolean inheritedBySubagents() {
        return true;
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

    /**
     * Returns the set of all memory IDs that have been tracked by this monitor,
     * including successful, failed, and ongoing executions.
     */
    public Set<Object> allMemoryIds() {
        Set<Object> ids = new LinkedHashSet<>();
        ids.addAll(successfulExecutions.keySet());
        ids.addAll(failedExecutions.keySet());
        ids.addAll(ongoingExecutions.keySet());
        return Collections.unmodifiableSet(ids);
    }

    /**
     * Returns all executions (successful, failed, and ongoing) for a given memory ID.
     */
    public List<MonitoredExecution> allExecutionsFor(AgenticScope agenticScope) {
        return allExecutionsFor(agenticScope.memoryId());
    }

    /**
     * Returns all executions (successful, failed, and ongoing) for a given memory ID.
     */
    public List<MonitoredExecution> allExecutionsFor(Object memoryId) {
        List<MonitoredExecution> all = new ArrayList<>(successfulExecutionsFor(memoryId));
        all.addAll(failedExecutionsFor(memoryId));
        MonitoredExecution ongoing = ongoingExecutionFor(memoryId);
        if (ongoing != null) {
            all.add(ongoing);
        }
        return all;
    }
}
