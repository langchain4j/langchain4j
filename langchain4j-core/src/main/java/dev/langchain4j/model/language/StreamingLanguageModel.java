package dev.langchain4j.model.language;

import dev.langchain4j.WillChangeSoon;
import dev.langchain4j.model.StreamingResultHandler;
import dev.langchain4j.model.input.Prompt;

public interface StreamingLanguageModel {

    @WillChangeSoon("Most probably StreamingResultHandler will be replaced with fluent API")
    void process(String text, StreamingResultHandler handler);

    @WillChangeSoon("Most probably StreamingResultHandler will be replaced with fluent API")
    void process(Prompt prompt, StreamingResultHandler handler);

    @WillChangeSoon("Most probably StreamingResultHandler will be replaced with fluent API")
    void process(Object structuredPrompt, StreamingResultHandler handler);
}
