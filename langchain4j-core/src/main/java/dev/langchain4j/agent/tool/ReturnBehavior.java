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
     */
    IMMEDIATE,

    /**
     * Returns immediately to the caller when the annotated tool is the <b>last</b> tool called
     * in a multi-tool batch response, and it completed without error. When placed last, the model
     * has intentionally signalled completion after all side-effect tools have run.
     * <p>
     * If the tool appears earlier in the batch (not last), execution continues normally as if
     * {@link #TO_LLM} were used.
     * <p>
     * Like {@link #IMMEDIATE}, this is only allowed on AI services returning
     * {@code dev.langchain4j.service.Result}.
     */
    IMMEDIATE_IF_LAST;
}
