package dev.langchain4j.spi.prompt.structured;

import dev.langchain4j.model.input.Prompt;

public interface StructuredPromptFactory {

    Prompt toPrompt(Object structuredPrompt);
}
