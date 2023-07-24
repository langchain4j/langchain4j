package dev.langchain4j.model.language;

import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.input.Prompt;

import static dev.langchain4j.model.input.structured.StructuredPromptProcessor.toPrompt;

public interface StreamingLanguageModel {

    void process(String text, StreamingResponseHandler handler);

    default void process(Prompt prompt, StreamingResponseHandler handler) {
        process(prompt.text(), handler);
    }

    default void process(Object structuredPrompt, StreamingResponseHandler handler) {
        process(toPrompt(structuredPrompt), handler);
    }
}
