package dev.langchain4j.spi.prompt.structured;

import dev.langchain4j.Internal;
import dev.langchain4j.model.input.Prompt;

/**
 * Represents a factory for structured prompts.
 */
@Internal
public interface StructuredPromptFactory {

    /**
     * Converts the given structured prompt to a prompt.
     * @param structuredPrompt the structured prompt.
     * @return the prompt.
     */
    Prompt toPrompt(Object structuredPrompt);
}
