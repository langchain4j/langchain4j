package dev.langchain4j.service.guardrail;

import dev.langchain4j.Experimental;
import dev.langchain4j.guardrail.ToolInputGuardrail;
import dev.langchain4j.guardrail.ToolOutputGuardrail;

/**
 * The stage of the guardrails of a tool call: the guardrails that run before the tool (input) or after it (output).
 * It tells which of the two ended a call, so that a caller does not have to work it out from its own bookkeeping.
 *
 * @see ToolInputGuardrail
 * @see ToolOutputGuardrail
 * @since 1.25.0
 */
@Experimental
public enum ToolGuardrailStage {

    /**
     * The {@link ToolInputGuardrail}s, which run before the tool.
     */
    INPUT,

    /**
     * The {@link ToolOutputGuardrail}s, which run after the tool.
     */
    OUTPUT
}
