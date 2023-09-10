package dev.langchain4j.model.language;

import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.input.Prompt;

/**
 * Represents a language model that has a simple text interface (as opposed to a chat interface)
 * and can stream a response one token at a time.
 * It is recommended to use the {@link dev.langchain4j.model.chat.StreamingChatLanguageModel} instead,
 * as it offers more features.
 */
public interface StreamingLanguageModel {

    void generate(String prompt, StreamingResponseHandler<String> handler);

    default void generate(Prompt prompt, StreamingResponseHandler<String> handler) {
        generate(prompt.text(), handler);
    }
}
