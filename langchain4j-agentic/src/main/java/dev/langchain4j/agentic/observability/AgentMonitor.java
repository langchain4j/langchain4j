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
public class AgentMonitor {

    private static final int DEFAULT_MAX_RETAINED_EXECUTIONS = 100;

    private volatile int maxRetainedExecutions = DEFAULT_MAX_RETAINED_EXECUTIONS;
    private AgentInstance rootAgent;

    private final Map<Object, List<MonitoredExecution>> successfulExecutions;
    private final Map<Object, List<MonitoredExecution>> failedExecutions;
    private final Map<Object, MonitoredExecution> ongoingExecutions = new ConcurrentHashMap<>();
    private final MonitoringListener listener = new MonitoringListener(this);

    public AgentMonitor() {
        this.successfulExecutions = createBoundedMap();
        this.failedExecutions = createBoundedMap();
    }

    public AgentListener asListener() {
        return listener;
    }

    @Internal
    public static AgentMonitor from(AgentListener listener) {
        MonitoringListener ml = ComposedAgentListener.listenerOfType(listener, MonitoringListener.class);
        return ml != null ? ml.monitor : null;
    }

    /**
     * Sets the maximum number of completed executions (per outcome: successful or failed)
     * retained by this monitor. When the limit is exceeded, the oldest entries are evicted
     * automatically. If the new limit is lower than the current number of retained executions,
     * excess entries are evicted immediately.
     *
     * <p>Defaults to 100.
     *
     * @param maxRetainedExecutions the maximum number of retained executions per outcome, must be &ge; 0
     * @throws IllegalArgumentException if {@code maxRetainedExecutions} is negative
     */
    public void setMaxRetainedExecutions(int maxRetainedExecutions) {
        if (maxRetainedExecutions < 0) {
            throw new IllegalArgumentException("maxRetainedExecutions must be >= 0");
        }
        this.maxRetainedExecutions = maxRetainedExecutions;
        trimToSize(successfulExecutions, maxRetainedExecutions);
        trimToSize(failedExecutions, maxRetainedExecutions);
    }

    /**
     * Removes all retained successful and failed executions.
     * Ongoing executions are not affected.
     */
    public void clear() {
        successfulExecutions.clear();
        failedExecutions.clear();
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
        return Collections.synchronizedMap(new LinkedHashMap<>() {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Object, List<MonitoredExecution>> eldest) {
                return size() > maxRetainedExecutions;
            }
        });
    }

    @Internal
    public void setRootAgent(AgentInstance rootAgent) {
        this.rootAgent = rootAgent;
    }

    AgentInstance rootAgent() {
        return rootAgent;
    }

    private void beforeAgentInvocation(AgentRequest agentRequest) {
        Object memoryId = agentRequest.agenticScope().memoryId();
        MonitoredExecution candidate = new MonitoredExecution(agentRequest);
        MonitoredExecution existing = ongoingExecutions.putIfAbsent(memoryId, candidate);
        if (existing != null) {
            existing.beforeAgentInvocation(agentRequest);
        }
    }

    private void afterAgentInvocation(AgentResponse agentResponse) {
        Object memoryId = agentResponse.agenticScope().memoryId();
        MonitoredExecution execution = ongoingExecutions.get(memoryId);
        execution.afterAgentInvocation(agentResponse);
        if (execution.done()) {
            ongoingExecutions.remove(memoryId, execution);
            successfulExecutions
                    .computeIfAbsent(memoryId, k -> Collections.synchronizedList(new ArrayList<>()))
                    .add(execution);
        }
    }

    private void onAgentInvocationError(AgentInvocationError agentInvocationError) {
        Object memoryId = agentInvocationError.agenticScope().memoryId();
        MonitoredExecution execution = ongoingExecutions.remove(memoryId);
        if (execution != null) {
            execution.onAgentInvocationError(agentInvocationError);
            failedExecutions
                    .computeIfAbsent(memoryId, k -> Collections.synchronizedList(new ArrayList<>()))
                    .add(execution);
        }
    }

    private void afterAgentToolExecution(AfterAgentToolExecution afterAgentToolExecution) {
        Object memoryId = afterAgentToolExecution.agenticScope().memoryId();
        MonitoredExecution execution = ongoingExecutions.get(memoryId);
        if (execution != null) {
            execution.afterToolExecution(afterAgentToolExecution);
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
        synchronized (successfulExecutions) {
            return successfulExecutions.values().stream().flatMap(List::stream).toList();
        }
    }

    public List<MonitoredExecution> successfulExecutionsFor(AgenticScope agenticScope) {
        return successfulExecutionsFor(agenticScope.memoryId());
    }

    public List<MonitoredExecution> successfulExecutionsFor(Object memoryId) {
        return successfulExecutions.getOrDefault(memoryId, List.of());
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
        return failedExecutions.getOrDefault(memoryId, List.of());
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

    static class MonitoringListener implements AgentListener {

        private final AgentMonitor monitor;

        MonitoringListener(AgentMonitor monitor) {
            this.monitor = monitor;
        }

        @Override
        public void beforeAgentInvocation(AgentRequest agentRequest) {
            monitor.beforeAgentInvocation(agentRequest);
        }

        @Override
        public void afterAgentInvocation(AgentResponse agentResponse) {
            monitor.afterAgentInvocation(agentResponse);
        }

        @Override
        public void onAgentInvocationError(AgentInvocationError agentInvocationError) {
            monitor.onAgentInvocationError(agentInvocationError);
        }

        @Override
        public void afterAgentToolExecution(AfterAgentToolExecution afterAgentToolExecution) {
            monitor.afterAgentToolExecution(afterAgentToolExecution);
        }

        @Override
        public boolean inheritedBySubagents() {
            return true;
        }
    }
}
