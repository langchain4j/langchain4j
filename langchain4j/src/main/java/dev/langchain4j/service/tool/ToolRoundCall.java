package dev.langchain4j.service.tool;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.observability.api.event.CompensationReason;
import dev.langchain4j.service.guardrail.GuardedToolExecution;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One call of a tool-calling round, at its position in the order of the LLM: the request it was executed with, the
 * result the LLM sees, and what the compensation of the round needs beyond that result.
 * <p>
 * The calls of a round are handled by position, not looked up by request: two calls of the LLM can be executed with
 * equal requests (an input guardrail can rewrite two calls without an id to the same arguments), and each keeps its own
 * result.
 * </p>
 * <p>
 * The result the LLM sees decides whether a round has a failed step. The result of the tool as it ran decides whether
 * the tool is compensable, and is what its compensating action receives, so a tool whose output a guardrail rejected is
 * rolled back with its original result.
 * </p>
 *
 * @param request          the request the call was executed with
 * @param result           the result the LLM sees, after the tool guardrails
 * @param toolResult       the result of the tool before its output guardrails, or {@code null} when the tool did not
 *                         run or the round has no record of it
 * @param guardrailFailure why the call rolls the other tools back when a tool guardrail turned it into an error result,
 *                         or {@code null}
 * @param execution        the execution of the call through its tool guardrails, or {@code null} when it has none
 *                         (the call failed with an exception, or the round has no record of it)
 */
record ToolRoundCall(
        ToolExecutionRequest request,
        ToolExecutionResult result,
        ToolExecutionResult toolResult,
        CompensationReason guardrailFailure,
        GuardedToolExecution execution) {

    /**
     * A call whose result is the result of its tool, because no tool guardrail applied to it.
     */
    static ToolRoundCall unguarded(ToolExecutionRequest request, ToolExecutionResult result) {
        return new ToolRoundCall(request, result, null, null, null);
    }

    /**
     * The calls of a round reported as requests and results by request, for the methods that take them that way.
     */
    static List<ToolRoundCall> unguarded(
            List<ToolExecutionRequest> requests, Map<ToolExecutionRequest, ToolExecutionResult> results) {
        return requests.stream()
                .map(request -> unguarded(request, results.get(request)))
                .toList();
    }

    /**
     * The result of the tool as it ran, or the result the LLM sees when the round has no record of it.
     */
    ToolExecutionResult ranResult() {
        return Optional.ofNullable(toolResult).orElse(result);
    }

    /**
     * The text that the LLM gets for a text of the result of the call (see
     * {@link GuardedToolExecution#resultTextForLlm(String)}).
     */
    String resultTextForLlm(String resultText) {
        return Optional.ofNullable(execution)
                .map(guarded -> guarded.resultTextForLlm(resultText))
                .orElse(resultText);
    }

    /**
     * Whether a tool output guardrail replaced the result of the tool, which then no longer carries the object the
     * tool returned.
     */
    boolean isResultRewritten() {
        return (toolResult != null) && (result != toolResult);
    }

    /**
     * Whether the tool ran successfully and so may need to be rolled back, whatever its guardrails made of the result.
     */
    boolean hasRunSuccessfully() {
        return !ranResult().isError();
    }

    /**
     * Why the call rolls the other tools back when it failed.
     */
    CompensationReason compensationReason() {
        return Optional.ofNullable(guardrailFailure).orElse(CompensationReason.TOOL_EXECUTION_FAILED);
    }
}
