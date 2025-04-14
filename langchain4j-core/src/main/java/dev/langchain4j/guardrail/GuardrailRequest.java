package dev.langchain4j.guardrail;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import java.util.Map;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.rag.AugmentationResult;
import org.jspecify.annotations.Nullable;

/**
 * Represents the parameter passed to {@link Guardrail#validate(GuardrailRequest)}} in order to validate an interaction
 * between a user and the LLM.
 */
public interface GuardrailRequest<P extends GuardrailRequest<P>> {
    /**
     * Represents the common parameters shared across guardrail checks when validating interactions
     * between a user and a language model. This record encapsulates the chat memory, user message
     * template, and additional variables required for guardrail processing.
     *
     * @param chatMemory          An optional {@link ChatMemory} instance representing the contextual
     *                            memory of the conversation.
     * @param augmentationResult  An optional {@link AugmentationResult} representing the result from a RAG pipeline.
     * @param userMessageTemplate A string containing the user's message template. It must be non-null.
     * @param variables           A map containing additional variables or contextual data. It must be non-null.
     */
    record CommonGuardrailParams(
            @Nullable ChatMemory chatMemory,
            @Nullable AugmentationResult augmentationResult,
            String userMessageTemplate,
            Map<String, Object> variables) {

        public CommonGuardrailParams {
            ensureNotNull(userMessageTemplate, "userMessageTemplate");
            ensureNotNull(variables, "variables");
        }
    }

    /**
     * Retrieves the common parameters that are shared across guardrail checks.
     *
     * @return an instance of {@code CommonGuardrailParams} containing shared parameters such as chat memory,
     *         user message template, and additional variables.
     */
    CommonGuardrailParams commonParams();

    /**
     * Recreate this guardrail param with the given input or output text.
     *
     * @param text
     *            The text of the rewritten param.
     *
     * @return A clone of this guardrail params with the given input or output text.
     */
    P withText(String text);
}
