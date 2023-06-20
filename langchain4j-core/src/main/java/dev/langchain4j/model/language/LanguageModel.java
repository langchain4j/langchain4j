package dev.langchain4j.model.language;

import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.output.Result;

public interface LanguageModel {

    Result<String> process(String text);

    Result<String> process(Prompt prompt);

    Result<String> process(Object structuredPrompt);
}
