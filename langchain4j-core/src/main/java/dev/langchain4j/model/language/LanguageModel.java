package dev.langchain4j.model.language;

import dev.langchain4j.model.input.Prompt;

public interface LanguageModel {

    String process(String text);

    String process(Prompt prompt);

    String process(Object structuredPrompt);
}
