package dev.langchain4j.model.language;

import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.input.Prompt;

/**
 * Represents a language model that has a simple text interface (as opposed to a chat interface)
 * and can stream a response one token at a time.
 * It is recommended to use the {@link StreamingChatModel} instead,
 * as it offers more features.
 */
public interface StreamingLanguageModel {

    /**
     * Generates a response from the model based on a prompt.
     *
     * @param prompt  The prompt.
     * @param handler The handler for streaming the response.
     */
    void generate(String prompt, StreamingResponseHandler<String> handler);

    /**
     * Generates a response from the model based on a prompt.
     *
     * @param prompt  The prompt.
     * @param handler The handler for streaming the response.
     */
    default void generate(Prompt prompt, StreamingResponseHandler<String> handler) {
        generate(prompt.text(), handler);
    }
}
