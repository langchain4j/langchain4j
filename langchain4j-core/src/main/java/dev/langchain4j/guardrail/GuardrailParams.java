package dev.langchain4j.guardrail;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.rag.AugmentationResult;
import org.jspecify.annotations.Nullable;

/**
 * Represents the parameter passed to {@link Guardrail#validate(GuardrailParams)}} in order to validate an interaction
 * between a user and the LLM.
 */
public interface GuardrailParams<P extends GuardrailParams<P>> {
    /**
     * @return the memory, can be {@code null} or empty
     */
    @Nullable
    ChatMemory chatMemory();

    /**
     * @return the augmentation result, can be {@code null}
     */
    @Nullable
    AugmentationResult augmentationResult();

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
