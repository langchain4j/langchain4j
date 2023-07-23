package dev.langchain4j.model.language;

import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.input.Prompt;

public interface StreamingLanguageModel {

    void process(String text, StreamingResponseHandler handler);

    void process(Prompt prompt, StreamingResponseHandler handler);

    void process(Object structuredPrompt, StreamingResponseHandler handler);
}
