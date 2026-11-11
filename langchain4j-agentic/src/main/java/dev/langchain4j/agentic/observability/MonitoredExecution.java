package dev.langchain4j.agentic.observability;

import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import dev.langchain4j.agentic.scope.AgenticScope;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a monitored execution of an agentic system, tracking the top-level agent invocation
 * and any other nested invocations, along with all the invocation currently in progress and
 * any errors that occur during execution.
 * <p>
 * The execution stays ongoing while the agentic system is suspended, for instance waiting for a human: the
 * invocations in progress at that moment, the root agent and the workflows containing the suspended step, are
 * marked as {@link AgentInvocation#suspended() suspended} and continue when their agents are invoked again to
 * resume the system, so their duration spans the suspension.
 */
public class MonitoredExecution {

    private final AgentInvocation topLevelInvocations;

    private final Map<Object, AgentInvocation> ongoingInvocations = new ConcurrentHashMap<>();
    private final Set<Object> submittedAgentIds = ConcurrentHashMap.newKeySet();
    private final Map<Object, Integer> runningAsyncAgents = new ConcurrentHashMap<>();

    private AgentInvocationError agentInvocationError;
    private volatile boolean unrecoveredAsyncFailure;

    MonitoredExecution(AgentRequest firstAgentRequest) {
        this.topLevelInvocations = new AgentInvocation(firstAgentRequest);
        ongoingInvocations.put(firstAgentRequest.agentId(), this.topLevelInvocations);
    }

    /**
     * Records the submission of an asynchronous agent, before its executor starts it: the invocation is nested under
     * its parent, still ongoing at that point, and keeps this execution ongoing until the agent completes, also when
     * the {@link #beforeAgentInvocation} of the agent comes after its parent, or the root call, completed.
     */
    void asyncAgentSubmitted(AgentRequest agentRequest) {
        beforeAgentInvocation(agentRequest);
        submittedAgentIds.add(agentRequest.agentId());
        runningAsyncAgents.merge(agentRequest.agentId(), 1, Integer::sum);
    }

    /**
     * Records the completion of an asynchronous agent recorded by {@link #asyncAgentSubmitted}, after its error
     * handler ran if it failed: {@code failed} tells whether a failure was not recovered, which records the execution
     * as failed, whether that agent failed before or after the root call ended.
     */
    void asyncAgentCompleted(String agentId, boolean failed) {
        runningAsyncAgents.computeIfPresent(agentId, (id, running) -> running == 1 ? null : running - 1);
        if (failed) {
            unrecoveredAsyncFailure = true;
        }
    }

    boolean hasRunningAsyncAgents() {
        return !runningAsyncAgents.isEmpty();
    }

    /**
     * Records the suspension of the agentic system: the invocations in progress stay ongoing, marked as suspended,
     * and continue when their agents are invoked again to resume the system. The asynchronous agents still running
     * are not waiting, so their invocations are left alone. Notified once per level of the agents containing the
     * suspended step, and marking an invocation already suspended is a no-op.
     */
    void agenticSystemSuspended() {
        ongoingInvocations.forEach((agentId, invocation) -> {
            if (!runningAsyncAgents.containsKey(agentId)) {
                invocation.markSuspended();
            }
        });
    }

    /**
     * Ends the invocations still suspended once the root agent ended: nothing will continue them, because the resumed
     * run did not invoke their agents again, like a branch of a router whose condition changed, or because they
     * suspended after the root call returned, like an asynchronous agent whose result nobody read.
     */
    void endSuspendedInvocationsIfRootEnded() {
        if (ongoingInvocations.containsKey(topLevelInvocations.agent().agentId())) {
            return;
        }
        ongoingInvocations.values().removeIf(invocation -> {
            if (!invocation.suspended()) {
                return false;
            }
            invocation.markResumed();
            return true;
        });
    }

    void beforeAgentInvocation(AgentRequest agentRequest) {
        if (submittedAgentIds.remove(agentRequest.agentId())) {
            // the invocation was recorded at the submission of this asynchronous agent
            return;
        }
        AgentInvocation suspendedInvocation = ongoingInvocations.get(agentRequest.agentId());
        if (suspendedInvocation != null && suspendedInvocation.suspended()) {
            // the agent is invoked again to resume the suspended agentic system: its invocation continues
            suspendedInvocation.markResumed();
            return;
        }
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
        AgentInvocation failedInvocation = ongoingInvocations.remove(agentInvocationError.agentId());
        if (failedInvocation != null) {
            failedInvocation.cancelSuspension();
        }
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

    /**
     * Returns {@code true} while the agentic system is suspended, for instance waiting for a human, and this
     * execution waits to be resumed by a new invocation of its root agent.
     */
    public boolean suspended() {
        return topLevelInvocations.suspended();
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
