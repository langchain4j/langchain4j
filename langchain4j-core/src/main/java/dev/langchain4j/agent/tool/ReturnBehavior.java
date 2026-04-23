package dev.langchain4j.agent.tool;

import dev.langchain4j.Experimental;

/**
 * Per-tool setting controlling what happens with a tool's result after execution.
 * Configured via {@link Tool#returnBehavior()}. TODO
 * <p>
 * In the default case ({@link #TO_LLM}), every tool result is appended to the conversation
 * and sent back to the LLM for further processing — AI Service execution loop runs another turn.
 * With {@link #IMMEDIATE} or {@link #IMMEDIATE_IF_LAST}, certain tool-call patterns
 * short-circuit the loop and return tool call result(s) directly to the caller inside the
 * {@code dev.langchain4j.service.Result}.
 * <p>
 * <b>Halt rule</b> applied after each LLM response: AI Service execution loop halts iff
 * <ol>
 *   <li>no tool in the response errored, AND</li>
 *   <li>either the last tool is {@link #IMMEDIATE_IF_LAST}, or every tool is
 *       {@link #IMMEDIATE}/{@link #IMMEDIATE_IF_LAST} (no {@link #TO_LLM} mixed in).</li>
 * </ol>
 * <p>
 * <b>Halt vs. reprocess for every order of behaviors</b> (no errors), as exercised by
 * {@code ReturnBehaviorCombinationsTest}:
 * <pre>
 *   [TO_LLM]                                 -&gt; reprocess
 *   [TO_LLM, TO_LLM]                         -&gt; reprocess
 *   [IMMEDIATE]                              -&gt; halt
 *   [IMMEDIATE, IMMEDIATE]                   -&gt; halt
 *   [TO_LLM, IMMEDIATE]                      -&gt; reprocess
 *   [IMMEDIATE, TO_LLM]                      -&gt; reprocess
 *   [IMMEDIATE_IF_LAST]                      -&gt; halt
 *   [IMMEDIATE_IF_LAST, IMMEDIATE_IF_LAST]   -&gt; halt
 *   [TO_LLM, IMMEDIATE_IF_LAST]              -&gt; halt
 *   [IMMEDIATE_IF_LAST, TO_LLM]              -&gt; reprocess
 *   [IMMEDIATE, IMMEDIATE_IF_LAST]           -&gt; halt
 *   [IMMEDIATE_IF_LAST, IMMEDIATE]           -&gt; halt
 *   [TO_LLM, IMMEDIATE, IMMEDIATE_IF_LAST]   -&gt; halt
 *   [TO_LLM, IMMEDIATE_IF_LAST, IMMEDIATE]   -&gt; reprocess
 *   [IMMEDIATE, TO_LLM, IMMEDIATE_IF_LAST]   -&gt; halt
 *   [IMMEDIATE, IMMEDIATE_IF_LAST, TO_LLM]   -&gt; reprocess
 *   [IMMEDIATE_IF_LAST, TO_LLM, IMMEDIATE]   -&gt; reprocess
 *   [IMMEDIATE_IF_LAST, IMMEDIATE, TO_LLM]   -&gt; reprocess
 * </pre>
 * <p>
 * <b>Any tool error forces reprocess</b> regardless of behaviors, so the LLM
 * can react to the error on the next turn.
 * <p>
 * {@link #IMMEDIATE} and {@link #IMMEDIATE_IF_LAST} are only allowed on AI services declaring
 * {@code dev.langchain4j.service.Result} as their return type. Using either on a service with
 * a different return type causes an {@code IllegalConfigurationException} the first time a
 * halt would occur.
 */
@Experimental
public enum ReturnBehavior {

    /**
     * The tool result is sent back to the LLM for further processing — AI Service execution loop
     * continues. This is the default behavior.
     */
    TO_LLM,

    /**
     * Halts AI Service execution loop and returns tool call result(s) to the caller (inside the
     * {@code dev.langchain4j.service.Result}) when the entire response is halt-causing —
     * i.e. every tool in the response is {@code IMMEDIATE} or {@link #IMMEDIATE_IF_LAST},
     * and no tool errored.
     * <p>
     * A single {@link #TO_LLM} tool anywhere in the response prevents the halt and the loop
     * runs another turn. Errors in any tool also prevent the halt so the LLM can react to
     * the error.
     * <p>
     * Examples (full matrix in the {@link ReturnBehavior class-level Javadoc}):
     * <pre>
     *   [IMMEDIATE]                              -&gt; halt
     *   [IMMEDIATE, IMMEDIATE]                   -&gt; halt
     *   [IMMEDIATE, IMMEDIATE_IF_LAST]           -&gt; halt   (both halt-causing)
     *   [IMMEDIATE_IF_LAST, IMMEDIATE]           -&gt; halt   (both halt-causing)
     *   [TO_LLM, IMMEDIATE]                      -&gt; reprocess
     *   [IMMEDIATE, TO_LLM]                      -&gt; reprocess
     * </pre>
     * <p>
     * Only allowed on AI services returning {@code dev.langchain4j.service.Result}.
     */
    IMMEDIATE,

    /**
     * Halts AI Service execution loop when this tool is positioned <b>last</b> in the LLM response.
     * Intended for tools the LLM uses to explicitly close an action sequence — placing the tool last is the LLM's
     * signal that no further LLM processing is needed.
     * <p>
     * Also counts as a halt-causing tool for the all-halt-causing rule of {@link #IMMEDIATE}:
     * a response made up only of {@code IMMEDIATE} and/or {@code IMMEDIATE_IF_LAST} tools
     * halts regardless of which one is last.
     * <p>
     * If positioned anywhere other than last, AND any other tool in the response is
     * {@link #TO_LLM}, the loop runs another turn — all tool call results (including this one)
     * are sent to the LLM. Errors in any tool also prevent the halt so the LLM can react to
     * the error.
     * <p>
     * Examples (full matrix in the {@link ReturnBehavior class-level Javadoc}):
     * <pre>
     *   [IMMEDIATE_IF_LAST]                      -&gt; halt
     *   [TO_LLM, IMMEDIATE_IF_LAST]              -&gt; halt   (last is IMMEDIATE_IF_LAST)
     *   [IMMEDIATE_IF_LAST, IMMEDIATE_IF_LAST]   -&gt; halt
     *   [IMMEDIATE_IF_LAST, IMMEDIATE]           -&gt; halt   (all halt-causing)
     *   [IMMEDIATE_IF_LAST, TO_LLM]              -&gt; reprocess  (not last; TO_LLM disqualifies all-rule)
     *   [TO_LLM, IMMEDIATE_IF_LAST, IMMEDIATE]   -&gt; reprocess  (not last; TO_LLM disqualifies all-rule)
     * </pre>
     * <p>
     * Only allowed on AI services returning {@code dev.langchain4j.service.Result}.
     */
    IMMEDIATE_IF_LAST;
}
