package dev.langchain4j.service.tool;

import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.observability.api.event.CompensationReason;
import java.util.Map;
import java.util.Optional;

/**
 * What the compensation of a tool-calling round needs to know beyond the result the LLM sees: the result of each tool
 * before its output guardrails, and the calls that a tool guardrail turned into an error result.
 * <p>
 * The result the LLM sees decides whether a round has a failed step. The result of the tool as it ran decides whether
 * the tool is compensable, and is what its compensating action receives, so a tool whose output a guardrail rejected is
 * rolled back with its original result.
 * </p>
 *
 * @param originalResults the result of each call whose tool ran, before its output guardrails, by the call as it was
 *                        executed. Without an entry the tool did not run
 * @param guardrailFailures the reason to compensate for each call that a tool guardrail turned into an error result,
 *                          by the call as it was executed
 */
@Internal
public record ToolRoundCompensation(
        Map<ToolExecutionRequest, ToolExecutionResult> originalResults,
        Map<ToolExecutionRequest, CompensationReason> guardrailFailures) {

    /**
     * A round whose results are the results of its tools, because no tool guardrail applied to it.
     */
    public static final ToolRoundCompensation NONE = new ToolRoundCompensation(Map.of(), Map.of());

    public ToolRoundCompensation {
        originalResults = Map.copyOf(originalResults);
        guardrailFailures = Map.copyOf(guardrailFailures);
    }

    /**
     * The result of the tool of {@code request} as it ran, or {@code guardedResult} when the round has no record of it.
     */
    ToolExecutionResult ranResult(ToolExecutionRequest request, ToolExecutionResult guardedResult) {
        return Optional.ofNullable(originalResults.get(request)).orElse(guardedResult);
    }

    /**
     * Whether the tool of {@code request} ran successfully and so may need to be rolled back, whatever its guardrails
     * made of the result.
     */
    boolean hasRunSuccessfully(ToolExecutionRequest request, ToolExecutionResult guardedResult) {
        return !ranResult(request, guardedResult).isError();
    }

    /**
     * Why a failed {@code request} rolls the other tools back.
     */
    CompensationReason reasonOf(ToolExecutionRequest request) {
        return guardrailFailures.getOrDefault(request, CompensationReason.TOOL_EXECUTION_FAILED);
    }
}
