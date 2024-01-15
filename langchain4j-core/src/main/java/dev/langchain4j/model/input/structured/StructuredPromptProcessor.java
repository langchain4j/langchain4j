package dev.langchain4j.model.input.structured;

import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.spi.ServiceHelper;
import dev.langchain4j.spi.prompt.structured.StructuredPromptFactory;

public class StructuredPromptProcessor {

    private static final StructuredPromptFactory FACTORY = ServiceHelper.loadService(
            StructuredPromptFactory.class, DefaultStructuredPromptFactory::new);

    public static Prompt toPrompt(Object structuredPrompt) {
        return FACTORY.toPrompt(structuredPrompt);
    }

}
