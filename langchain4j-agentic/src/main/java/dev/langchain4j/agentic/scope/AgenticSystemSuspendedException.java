package dev.langchain4j.agentic.scope;

/**
 * Thrown when a persistent agentic system suspends because it contains unresolved
 * {@link dev.langchain4j.agentic.internal.SuspendedResponse} entries (typically
 * from a HumanInTheLoop agent). The system's state has been checkpointed
 * and the calling thread is released.
 * <p>
 * To resume the agentic system:
 * <ol>
 *   <li>Provide the human response via {@link AgenticScope#completePendingResponse(String, Object)}
 *       or {@link AgenticScope#writeState(String, Object)}</li>
 *   <li>Re-invoke the agent method with the same memory ID</li>
 * </ol>
 */
public class AgenticSystemSuspendedException extends RuntimeException {

    private final AgenticScope scope;

    public AgenticSystemSuspendedException(AgenticScope scope) {
        super("Agentic system suspended: awaiting responses for " + scope.pendingResponseIds());
        this.scope = scope;
    }

    public AgenticScope scope() {
        return scope;
    }
}
