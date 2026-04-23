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
     * to further process it.
     * <p>
     * When the LLM returns multiple tool calls in a single response, execution halts only if
     * <b>all</b> requested tools are {@code IMMEDIATE} TODO and none of them errored.
     * <p>
     * An error in <b>any</b> tool call in the response (this tool or
     * any other) prevents the halt and lets the LLM react to the error on the next turn. TODO test, document
     * <p>
     * Immediate return is only allowed on AI services returning {@code dev.langchain4j.service.Result}.
     */
    IMMEDIATE,

    /**
     * Halts the execution loop when this tool is the <b>last</b> tool call in the LLM response,
     * returning immediately to the caller. When the tool appears in any other position within a
     * multi-tool response, all tool results (including this one) are sent back to the LLM as usual.
     * <p>
     * Intended for tools that the LLM uses to explicitly close an execution loop after
     * performing a sequence of other tool calls.
     * <p>
     * <b>Differences from {@link #IMMEDIATE}:</b>
     * <ul>
     *   <li>{@code IMMEDIATE} halts only when <b>every</b> tool in the response is
     *       {@code IMMEDIATE} — a single non-{@code IMMEDIATE} tool causes the loop to continue.</li>
     *   <li>{@code IMMEDIATE_IF_LAST} halts as long as <b>this</b> tool is positioned last,
     *       <b>regardless of the {@link ReturnBehavior} of the other tool calls</b> in the same
     *       response — their results are <b>not</b> sent back to the LLM and their behaviors
     *       are ignored. The contract is "the LLM placed me last on purpose to close out
     *       execution; honor that intent."</li>
     * </ul>
     * <p>
     * Like {@link #IMMEDIATE}, an error in <b>any</b> tool call in the response (this tool or
     * any other) prevents the halt and lets the LLM react to the error on the next turn. TODO test, document
     * <p>
     * Immediate return is only allowed on AI services returning {@code dev.langchain4j.service.Result}.
     */
    IMMEDIATE_IF_LAST;
}
