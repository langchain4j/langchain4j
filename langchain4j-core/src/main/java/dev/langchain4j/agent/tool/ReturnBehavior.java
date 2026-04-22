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
     * Like {@link #IMMEDIATE}, but halts execution only when this tool is the <b>last</b>
     * tool call in the LLM response. When the tool appears in any other position within a
     * multi-tool response, all tool results are sent back to the LLM as usual.
     * <p>
     * Intended for tools that the LLM uses to explicitly close an execution loop after
     * performing a sequence of other actions (e.g., {@code endExecutionAndGetFinalResult}).
     * Saves one round trip to the LLM compared to waiting for the LLM to issue the halt
     * tool on its own in the next turn.
     * <p>
     * Same return-type restriction as {@link #IMMEDIATE} applies: only allowed on AI services
     * returning {@code dev.langchain4j.service.Result}.
     */
    IMMEDIATE_IF_LAST;
}
