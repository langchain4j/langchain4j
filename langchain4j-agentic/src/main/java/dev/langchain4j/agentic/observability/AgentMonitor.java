package dev.langchain4j.agentic.observability;

import dev.langchain4j.Internal;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.scope.AgenticScope;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
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

    private static final int DEFAULT_MAX_RETAINED_SESSIONS = 100;

    private volatile int maxRetainedSessions = DEFAULT_MAX_RETAINED_SESSIONS;
    private AgentInstance rootAgent;

    private final Map<Object, List<MonitoredExecution>> successfulExecutions;
    private final Map<Object, List<MonitoredExecution>> failedExecutions;
    private final Map<Object, MonitoredExecution> ongoingExecutions = new ConcurrentHashMap<>();

    public AgentMonitor() {
        this.successfulExecutions = createBoundedMap();
        this.failedExecutions = createBoundedMap();
    }

    /**
     * Sets the maximum number of sessions (distinct memory IDs, per outcome: successful or failed)
     * retained by this monitor. When the limit is exceeded, the oldest sessions are evicted
     * automatically. If the new limit is lower than the current number of retained sessions,
     * excess entries are evicted immediately.
     *
     * <p>Defaults to 100.
     *
     * @param maxRetainedSessions the maximum number of retained sessions per outcome, must be &ge; 0
     * @throws IllegalArgumentException if {@code maxRetainedSessions} is negative
     */
    public void setMaxRetainedSessions(int maxRetainedSessions) {
        if (maxRetainedSessions < 0) {
            throw new IllegalArgumentException("maxRetainedSessions must be >= 0");
        }
        this.maxRetainedSessions = maxRetainedSessions;
        trimToSize(successfulExecutions, maxRetainedSessions);
        trimToSize(failedExecutions, maxRetainedSessions);
    }

    /**
     * Removes all retained successful and failed executions.
     * Ongoing executions are not affected.
     */
    public void clear() {
        synchronized (successfulExecutions) {
            successfulExecutions.clear();
        }
        synchronized (failedExecutions) {
            failedExecutions.clear();
        }
    }

    private static void trimToSize(Map<Object, ?> map, int maxSize) {
        synchronized (map) {
            var it = map.entrySet().iterator();
            while (map.size() > maxSize && it.hasNext()) {
                it.next();
                it.remove();
            }
        }
    }

    private Map<Object, List<MonitoredExecution>> createBoundedMap() {
        return new LinkedHashMap<>() {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Object, List<MonitoredExecution>> eldest) {
                return size() > maxRetainedSessions;
            }
        };
    }

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
        MonitoredExecution candidate = new MonitoredExecution(agentRequest);
        MonitoredExecution existing = ongoingExecutions.putIfAbsent(memoryId, candidate);
        if (existing != null) {
            existing.beforeAgentInvocation(agentRequest);
        }
    }

    @Override
    public void afterAgentInvocation(AgentResponse agentResponse) {
        Object memoryId = agentResponse.agenticScope().memoryId();
        MonitoredExecution execution = ongoingExecutions.get(memoryId);
        execution.afterAgentInvocation(agentResponse);
        if (execution.done()) {
            ongoingExecutions.remove(memoryId, execution);
            synchronized (successfulExecutions) {
                successfulExecutions
                        .computeIfAbsent(memoryId, k -> new ArrayList<>())
                        .add(execution);
            }
        }
    }

    @Override
    public void onAgentInvocationError(AgentInvocationError agentInvocationError) {
        Object memoryId = agentInvocationError.agenticScope().memoryId();
        MonitoredExecution execution = ongoingExecutions.get(memoryId);
        if (execution != null) {
            execution.onAgentInvocationError(agentInvocationError);
            if (execution.ongoingInvocations().isEmpty()) {
                ongoingExecutions.remove(memoryId, execution);
                synchronized (failedExecutions) {
                    failedExecutions
                            .computeIfAbsent(memoryId, k -> new ArrayList<>())
                            .add(execution);
                }
            }
        }
    }

    @Override
    public void afterAgentToolExecution(AfterAgentToolExecution afterAgentToolExecution) {
        Object memoryId = afterAgentToolExecution.agenticScope().memoryId();
        MonitoredExecution execution = ongoingExecutions.get(memoryId);
        if (execution != null) {
            execution.afterToolExecution(afterAgentToolExecution);
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
        synchronized (successfulExecutions) {
            return successfulExecutions.values().stream().flatMap(List::stream).toList();
        }
    }

    public List<MonitoredExecution> successfulExecutionsFor(AgenticScope agenticScope) {
        return successfulExecutionsFor(agenticScope.memoryId());
    }

    public List<MonitoredExecution> successfulExecutionsFor(Object memoryId) {
        synchronized (successfulExecutions) {
            return List.copyOf(successfulExecutions.getOrDefault(memoryId, List.of()));
        }
    }

    public List<MonitoredExecution> failedExecutions() {
        synchronized (failedExecutions) {
            return failedExecutions.values().stream().flatMap(List::stream).toList();
        }
    }

    public List<MonitoredExecution> failedExecutionsFor(AgenticScope agenticScope) {
        return failedExecutionsFor(agenticScope.memoryId());
    }

    public List<MonitoredExecution> failedExecutionsFor(Object memoryId) {
        synchronized (failedExecutions) {
            return List.copyOf(failedExecutions.getOrDefault(memoryId, List.of()));
        }
    }

    /**
     * Returns the set of all memory IDs that have been tracked by this monitor,
     * including successful, failed, and ongoing executions.
     */
    public Set<Object> allMemoryIds() {
        Set<Object> ids = new LinkedHashSet<>();
        synchronized (successfulExecutions) {
            ids.addAll(successfulExecutions.keySet());
        }
        synchronized (failedExecutions) {
            ids.addAll(failedExecutions.keySet());
        }
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
