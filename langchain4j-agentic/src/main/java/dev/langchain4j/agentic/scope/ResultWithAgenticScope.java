package dev.langchain4j.agentic.scope;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Holds the result of an agent invocation along with its associated {@link AgenticScope}.
 * This is useful for returning results from agents while also providing access to the cognitive
 * scope through which that result has been generated.
 * <p>
 * When the invocation is suspended ({@link #suspended()} returns {@code true}), the caller
 * can provide the human response and resume in a single call via
 * {@link #completePendingResponse(Object)}.
 *
 * @param <T> The type of the result.
 */
public final class ResultWithAgenticScope<T> {

    private final AgenticScope agenticScope;
    private final T result;
    private final boolean suspended;
    private final transient Supplier<ResultWithAgenticScope<T>> resumeCallback;

    public ResultWithAgenticScope(AgenticScope agenticScope, T result) {
        this(agenticScope, result, false, null);
    }

    public ResultWithAgenticScope(AgenticScope agenticScope, T result, boolean suspended) {
        this(agenticScope, result, suspended, null);
    }

    public ResultWithAgenticScope(AgenticScope agenticScope, T result, boolean suspended,
                                  Supplier<ResultWithAgenticScope<T>> resumeCallback) {
        this.agenticScope = agenticScope;
        this.result = result;
        this.suspended = suspended;
        this.resumeCallback = resumeCallback;
    }

    public AgenticScope agenticScope() {
        return agenticScope;
    }

    public T result() {
        return result;
    }

    public boolean suspended() {
        return suspended;
    }

    /**
     * Completes the single pending response and re-invokes the agentic system to resume execution.
     * The returned result may itself be suspended if the workflow has further human-in-the-loop steps.
     *
     * @param value the human response value
     * @return the result of the resumed invocation
     * @throws IllegalStateException if this result is not suspended, if there is not exactly one
     *         pending response, or if no resume callback is available (e.g. after a crash/restart)
     */
    public ResultWithAgenticScope<T> completePendingResponse(Object value) {
        return completePendingResponse(singlePendingResponseId(), value);
    }

    /**
     * Completes the pending response with the given ID and re-invokes the agentic system to resume execution.
     * The returned result may itself be suspended if the workflow has further human-in-the-loop steps.
     *
     * @param responseId the identifier of the pending response to complete
     * @param value the human response value
     * @return the result of the resumed invocation
     * @throws IllegalStateException if this result is not suspended or if no resume callback
     *         is available (e.g. after a crash/restart)
     */
    public ResultWithAgenticScope<T> completePendingResponse(String responseId, Object value) {
        if (!suspended) {
            throw new IllegalStateException("Cannot complete a pending response on a non-suspended result");
        }
        if (resumeCallback == null) {
            throw new IllegalStateException(
                    "No resume callback available. After a crash/restart, use AgenticScope.completePendingResponse() and re-invoke the agent method directly.");
        }
        agenticScope.completePendingResponse(responseId, value);
        return resumeCallback.get();
    }

    private String singlePendingResponseId() {
        var ids = agenticScope.pendingResponseIds();
        if (ids.size() != 1) {
            throw new IllegalStateException(
                    "Expected exactly 1 pending response, but found " + ids.size() + ": " + ids);
        }
        return ids.iterator().next();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ResultWithAgenticScope<?> that)) return false;
        return suspended == that.suspended
                && Objects.equals(agenticScope, that.agenticScope)
                && Objects.equals(result, that.result);
    }

    @Override
    public int hashCode() {
        return Objects.hash(agenticScope, result, suspended);
    }

    @Override
    public String toString() {
        return "ResultWithAgenticScope[agenticScope=" + agenticScope
                + ", result=" + result
                + ", suspended=" + suspended + "]";
    }
}
