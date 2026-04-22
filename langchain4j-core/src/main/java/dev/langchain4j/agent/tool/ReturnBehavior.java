package dev.langchain4j.agent.tool;

import dev.langchain4j.Experimental;

/**
 * Defines the behavior of a tool's return value when called by a language model.
 */
@Experimental
public enum ReturnBehavior {

    /**
     * The value returned by the tool is sent back to the LLM for further processing.
     * This is the default behavior.
     */
    TO_LLM,

    /**
     * Returns immediately to the caller the value returned by the tool without allowing the LLM
     * to further process it. Immediate return is only allowed on AI services returning {@code dev.langchain4j.service.Result},
     * while a {@code RuntimeException} will be thrown attempting to use a tool with immediate return with an
     * AI service having a different return type.
     * <p>
     * When the LLM returns multiple tool calls in a single response, execution halts only if
     * <b>all</b> requested tools are {@code IMMEDIATE} and none of them errored. If this tool is
     * meant to explicitly signal end-of-execution when placed last by the LLM (e.g. among several
     * other non-immediate tool calls), consider using {@link #IMMEDIATE_IF_LAST} instead.
     */
    IMMEDIATE,

    /**
     * Halts the execution loop when this tool is the <b>last</b> tool call in the LLM response,
     * returning immediately to the caller. When the tool appears in any other position within a
     * multi-tool response, all tool results (including this one) are sent back to the LLM as usual.
     * <p>
     * Intended for tools that the LLM uses to explicitly close an execution loop after
     * performing a sequence of other tool calls.
     * Saves one round trip to the LLM compared to waiting for the LLM to issue the halt
     * tool on its own in the next turn.
     * <p>
     * <b>Differences from {@link #IMMEDIATE}:</b>
     * <ul>
     *   <li>{@code IMMEDIATE} halts only when <b>every</b> tool in the response is
     *       {@code IMMEDIATE} and none errored — a single non-{@code IMMEDIATE} tool or any
     *       error elsewhere in the response causes the loop to continue.</li>
     *   <li>{@code IMMEDIATE_IF_LAST} halts if <b>this</b> tool is positioned last,
     *       <b>regardless of the other tool calls</b> in the same response: the other tools'
     *       results are <b>not</b> sent back to the LLM, their {@link ReturnBehavior} values
     *       are ignored, and errors in those other tools do <b>not</b> prevent the halt. TODO
     *       Only an error in <b>this</b> tool itself prevents the halt.
     *       The contract is "the LLM placed me last on purpose to close out execution;
     *       honor that intent."</li>
     * </ul>
     * <p>
     * Same return-type restriction as {@link #IMMEDIATE} applies: only allowed on AI services
     * returning {@code dev.langchain4j.service.Result}.
     */
    IMMEDIATE_IF_LAST;
}
