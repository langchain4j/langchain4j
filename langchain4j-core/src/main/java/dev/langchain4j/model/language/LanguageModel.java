package dev.langchain4j.model.language;

import dev.langchain4j.model.input.Prompt;

/**
 * Represents a LLM with a simple text interface.
 * It is recommended to use the ChatLanguageModel instead, as it offers greater capabilities.
 */
public interface LanguageModel {

    String process(String text);

    String process(Prompt prompt);

    String process(Object structuredPrompt);
}
