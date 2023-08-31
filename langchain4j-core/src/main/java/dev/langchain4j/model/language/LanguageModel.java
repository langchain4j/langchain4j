package dev.langchain4j.model.language;

import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.output.Result;

import static dev.langchain4j.model.input.structured.StructuredPromptProcessor.toPrompt;

/**
 * Represents a language model that has a simple text interface (as opposed to a chat interface).
 * It is recommended to use the {@link dev.langchain4j.model.chat.ChatLanguageModel} instead,
 * as it offers better capabilities.
 */
public interface LanguageModel {

    Result<String> generate(String prompt);

    default Result<String> generate(Prompt prompt) {
        return generate(prompt.text());
    }

    default Result<String> generate(Object structuredPrompt) {
        return generate(toPrompt(structuredPrompt));
    }
}
