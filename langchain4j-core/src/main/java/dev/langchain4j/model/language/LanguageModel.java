package dev.langchain4j.model.language;

import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.output.Response;

/**
 * Represents a language model that has a simple text interface (as opposed to a chat interface).
 * It is recommended to use the {@link dev.langchain4j.model.chat.ChatLanguageModel} instead,
 * as it offers more features.
 */
public interface LanguageModel {

    Response<String> generate(String prompt);

    default Response<String> generate(Prompt prompt) {
        return generate(prompt.text());
    }
}
