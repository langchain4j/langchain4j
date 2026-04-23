package dev.langchain4j.agent.tool;

import dev.langchain4j.Experimental;

/**
 * Per-tool setting controlling what happens with a tool's result after execution.
 * <p>
 * In the default case ({@link #TO_LLM}), every tool result is appended to the conversation
 * and sent back to the LLM for further processing — AI Service execution loop runs another turn.
 * With {@link #IMMEDIATE} or {@link #IMMEDIATE_IF_LAST}, certain tool-call patterns
 * short-circuit the loop and return tool call result(s) directly to the caller inside the
 * {@code dev.langchain4j.service.Result}.
 * <p>
 * <b>Immediate-return rule</b> applied after each LLM response: AI Service execution loop
 * returns immediately iff
 * <ol>
 *   <li>no tool in the response errored, AND</li>
 *   <li>either the last tool is {@link #IMMEDIATE_IF_LAST}, or every tool is
 *       {@link #IMMEDIATE}/{@link #IMMEDIATE_IF_LAST} (no {@link #TO_LLM} mixed in).</li>
 * </ol>
 * <p>
 * <b>Immediate return vs. reprocess for every order of behaviors</b> (no errors), as
 * exercised by {@code ReturnBehaviorCombinationsTest}:
 * <pre>
 *   [TO_LLM]                                 -&gt; reprocess
 *   [TO_LLM, TO_LLM]                         -&gt; reprocess
 *   [IMMEDIATE]                              -&gt; return immediately
 *   [IMMEDIATE, IMMEDIATE]                   -&gt; return immediately
 *   [TO_LLM, IMMEDIATE]                      -&gt; reprocess
 *   [IMMEDIATE, TO_LLM]                      -&gt; reprocess
 *   [IMMEDIATE_IF_LAST]                      -&gt; return immediately
 *   [IMMEDIATE_IF_LAST, IMMEDIATE_IF_LAST]   -&gt; return immediately
 *   [TO_LLM, IMMEDIATE_IF_LAST]              -&gt; return immediately
 *   [IMMEDIATE_IF_LAST, TO_LLM]              -&gt; reprocess
 *   [IMMEDIATE, IMMEDIATE_IF_LAST]           -&gt; return immediately
 *   [IMMEDIATE_IF_LAST, IMMEDIATE]           -&gt; return immediately
 *   [TO_LLM, IMMEDIATE, IMMEDIATE_IF_LAST]   -&gt; return immediately
 *   [TO_LLM, IMMEDIATE_IF_LAST, IMMEDIATE]   -&gt; reprocess
 *   [IMMEDIATE, TO_LLM, IMMEDIATE_IF_LAST]   -&gt; return immediately
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
 * a different return type causes an {@code IllegalConfigurationException} the first time an
 * immediate return would occur.
 */
@Experimental
public enum ReturnBehavior {

    /**
     * The tool result is sent back to the LLM for further processing — AI Service execution loop
     * continues. This is the default behavior.
     */
    TO_LLM,

    /**
     * Returns AI Service execution loop result(s) to the caller (inside the
     * {@code dev.langchain4j.service.Result}) when every tool in the response is
     * {@code IMMEDIATE} or {@link #IMMEDIATE_IF_LAST}, and no tool errored.
     * <p>
     * A single {@link #TO_LLM} tool anywhere in the response prevents the immediate return and
     * the loop runs another turn. Errors in any tool also prevent the immediate return so the
     * LLM can react to the error.
     * <p>
     * Examples (full matrix in the {@link ReturnBehavior class-level Javadoc}):
     * <pre>
     *   [IMMEDIATE]                              -&gt; return immediately
     *   [IMMEDIATE, IMMEDIATE]                   -&gt; return immediately
     *   [IMMEDIATE, IMMEDIATE_IF_LAST]           -&gt; return immediately   (every tool is IMMEDIATE/IMMEDIATE_IF_LAST)
     *   [IMMEDIATE_IF_LAST, IMMEDIATE]           -&gt; return immediately   (every tool is IMMEDIATE/IMMEDIATE_IF_LAST)
     *   [TO_LLM, IMMEDIATE]                      -&gt; reprocess
     *   [IMMEDIATE, TO_LLM]                      -&gt; reprocess
     * </pre>
     * <p>
     * Only allowed on AI services returning {@code dev.langchain4j.service.Result}.
     */
    IMMEDIATE,

    /**
     * Returns AI Service execution loop result(s) to the caller when this tool is positioned
     * <b>last</b> in the LLM response. Intended for tools the LLM uses to explicitly close an
     * action sequence — placing the tool last is the LLM's signal that no further LLM processing
     * is needed.
     * <p>
     * Also counts toward the all-immediate rule of {@link #IMMEDIATE}: a response made up only
     * of {@code IMMEDIATE} and/or {@code IMMEDIATE_IF_LAST} tools returns immediately regardless
     * of which one is last.
     * <p>
     * If positioned anywhere other than last, AND any other tool in the response is
     * {@link #TO_LLM}, the loop runs another turn — all tool call results (including this one)
     * are sent to the LLM. Errors in any tool also prevent the immediate return so the LLM can
     * react to the error.
     * <p>
     * Examples (full matrix in the {@link ReturnBehavior class-level Javadoc}):
     * <pre>
     *   [IMMEDIATE_IF_LAST]                      -&gt; return immediately
     *   [TO_LLM, IMMEDIATE_IF_LAST]              -&gt; return immediately   (last is IMMEDIATE_IF_LAST)
     *   [IMMEDIATE_IF_LAST, IMMEDIATE_IF_LAST]   -&gt; return immediately
     *   [IMMEDIATE_IF_LAST, IMMEDIATE]           -&gt; return immediately   (every tool is IMMEDIATE/IMMEDIATE_IF_LAST)
     *   [IMMEDIATE_IF_LAST, TO_LLM]              -&gt; reprocess  (not last; TO_LLM disqualifies all-immediate rule)
     *   [TO_LLM, IMMEDIATE_IF_LAST, IMMEDIATE]   -&gt; reprocess  (not last; TO_LLM disqualifies all-immediate rule)
     * </pre>
     * <p>
     * Only allowed on AI services returning {@code dev.langchain4j.service.Result}.
     */
    IMMEDIATE_IF_LAST;
}
