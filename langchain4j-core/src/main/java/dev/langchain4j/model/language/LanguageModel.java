package dev.langchain4j.model.language;

import dev.langchain4j.model.input.Prompt;

import static dev.langchain4j.model.input.structured.StructuredPromptProcessor.toPrompt;

/**
 * Represents a LLM that has a simple text interface (as opposed to a chat interface).
 * It is recommended to use the ChatLanguageModel instead, as it offers better capabilities.
 * More details: https://openai.com/blog/gpt-4-api-general-availability
 */
public interface LanguageModel {

    String process(String text);

    default String process(Prompt prompt) {
        return process(prompt.text());
    }

    default String process(Object structuredPrompt) {
        return process(toPrompt(structuredPrompt));
    }
}
