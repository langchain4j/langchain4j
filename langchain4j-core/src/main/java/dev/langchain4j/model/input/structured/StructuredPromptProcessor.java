package dev.langchain4j.model.input.structured;

import dev.langchain4j.Internal;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.spi.prompt.structured.StructuredPromptFactory;

import static dev.langchain4j.spi.ServiceHelper.loadFactories;

/**
 * Utility class for structured prompts.
 * Loads the {@link StructuredPromptFactory} SPI.
 */
@Internal
public class StructuredPromptProcessor {
    private StructuredPromptProcessor() {
    }

    private static final StructuredPromptFactory FACTORY = factory();

    private static StructuredPromptFactory factory() {
        for (StructuredPromptFactory factory : loadFactories(StructuredPromptFactory.class)) {
            return factory;
        }
        return new DefaultStructuredPromptFactory();
    }

    /**
     * Converts the given structured prompt to a prompt.
     *
     * @param structuredPrompt the structured prompt.
     * @return the prompt.
     */
    public static Prompt toPrompt(Object structuredPrompt) {
        return FACTORY.toPrompt(structuredPrompt);
    }
}
