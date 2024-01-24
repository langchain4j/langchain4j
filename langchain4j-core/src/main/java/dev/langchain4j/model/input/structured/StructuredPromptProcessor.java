package dev.langchain4j.model.input.structured;

import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.spi.ServiceHelper;
import dev.langchain4j.spi.prompt.structured.StructuredPromptFactory;

/**
 * Utility class for structured prompts.
 * Loads the {@link StructuredPromptFactory} SPI.
 */
public class StructuredPromptProcessor {
    private StructuredPromptProcessor() {}

    private static final StructuredPromptFactory FACTORY = ServiceHelper.loadService(
            StructuredPromptFactory.class, DefaultStructuredPromptFactory::new);

    /**
     * Converts the given structured prompt to a prompt.
     * @param structuredPrompt the structured prompt.
     * @return the prompt.
     */
    public static Prompt toPrompt(Object structuredPrompt) {
        return FACTORY.toPrompt(structuredPrompt);
    }
}
