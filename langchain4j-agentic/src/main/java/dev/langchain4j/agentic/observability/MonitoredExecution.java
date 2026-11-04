package dev.langchain4j.agentic.observability;

import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
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
    private volatile boolean unrecoveredAsyncFailure;

    MonitoredExecution(AgentRequest firstAgentRequest) {
        this.topLevelInvocations = new AgentInvocation(firstAgentRequest);
        ongoingInvocations.put(firstAgentRequest.agentId(), this.topLevelInvocations);
    }

    void beforeAgentInvocation(AgentRequest agentRequest) {
        AgentInvocation newInvocation = new AgentInvocation(agentRequest);
        addNestedInvocation(agentRequest.agent(), newInvocation);
        ongoingInvocations.put(agentRequest.agentId(), newInvocation);
    }

    void onAgentSkipped(AgentSkipped agentSkipped) {
        // a skipped agent is already finished, so it is never an ongoing invocation
        addNestedInvocation(agentSkipped.agent(), AgentInvocation.skipped(agentSkipped));
    }

    private void addNestedInvocation(AgentInstance agent, AgentInvocation newInvocation) {
        String parentId = agent.parent().agentId();
        AgentInvocation parentInvocation = ongoingInvocations.get(parentId);
        if (parentInvocation == null) {
            // the retry of an asynchronous agent is invoked after its parent completed: it is nested under the last
            // invocation of that parent
            parentInvocation = latestInvocationOf(topLevelInvocations, parentId);
        }
        if (parentInvocation == null) {
            throw new IllegalStateException("No ongoing parent invocation found for agent ID: " + parentId);
        }

        if (parentInvocation.agent().topology() == AgenticSystemTopology.LOOP) {
            // skipped invocations are counted too, so that an agent skipped in an iteration keeps the indexes right
            String agentId = agent.agentId();
            int count = 0;
            for (AgentInvocation existing : parentInvocation.nestedInvocations()) {
                if (agentId.equals(existing.agent().agentId())) {
                    count++;
                }
            }
            newInvocation.setIterationIndex(count);
        }

        parentInvocation.addNestedInvocation(newInvocation);
    }

    void afterAgentInvocation(AgentResponse agentResponse) {
        AgentInvocation finishedInvocation = ongoingInvocations.remove(agentResponse.agentId());
        if (finishedInvocation == null) {
            throw new IllegalStateException("No ongoing invocation found for agent ID: " + agentResponse.agentId());
        }
        finishedInvocation.finished(agentResponse);
    }

    private static AgentInvocation latestInvocationOf(AgentInvocation invocation, String agentId) {
        AgentInvocation latest = agentId.equals(invocation.agent().agentId()) ? invocation : null;
        for (AgentInvocation nested : invocation.nestedInvocations()) {
            AgentInvocation found = latestInvocationOf(nested, agentId);
            if (found != null) {
                latest = found;
            }
        }
        return latest;
    }

    /**
     * Records that an asynchronous agent of this execution failed and its error handler did not recover the failure:
     * the execution is then recorded as failed, whether that agent failed before or after the root call ended.
     */
    void asyncAgentFailed() {
        unrecoveredAsyncFailure = true;
    }

    boolean hasUnrecoveredAsyncFailure() {
        return unrecoveredAsyncFailure;
    }

    void onAgentInvocationError(AgentInvocationError agentInvocationError) {
        // Preserve the first (root-cause) error: when an error propagates up a sequential
        // chain, onAgentInvocationError is called once per agent in the chain, but only the
        // first call carries the original failing agent.
        if (this.agentInvocationError == null) {
            this.agentInvocationError = agentInvocationError;
        }
        ongoingInvocations.remove(agentInvocationError.agentId());
    }

    void afterToolExecution(AfterAgentToolExecution afterToolExecution) {
        String agentId = afterToolExecution.agentInstance().agentId();
        AgentInvocation invocation = ongoingInvocations.get(agentId);
        if (invocation != null) {
            invocation.addToolExecution(afterToolExecution.toolExecution());
        }
    }

    public Collection<AgentInvocation> ongoingInvocations() {
        return ongoingInvocations.values();
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

    /**
     * Returns the memory ID that identifies the session this execution belongs to.
     */
    public Object memoryId() {
        return agenticScope().memoryId();
    }

    public AgenticScope agenticScope() {
        return topLevelInvocations.agenticScope();
    }

    @Override
    public String toString() {
        return topLevelInvocations.toString();
    }
}
