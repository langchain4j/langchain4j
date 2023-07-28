package dev.langchain4j.model;

public interface StreamingResponseHandler {

    /**
     * This method is invoked each time LLM sends a token.
     *
     * @param token single token, part of a complete response
     */
    void onNext(String token);

    /**
     * This method is invoked when LLM decides to execute a tool.
     * It is supposed to work exclusively with the StreamingChatLanguageModel.
     *
     * @param name the name of the tool that LLM has chosen to execute
     */
    default void onToolName(String name) {
    }

    /**
     * This method is invoked each time LLM sends a token.
     * This is how the following string with arguments { "argument": "value" } can be streamed:
     * 1. "{"
     * 2. " \""
     * 3. "argument"
     * 4: "\":"
     * 5. " \""
     * 6. "value"
     * 7. "\" "
     * 8. "}"
     * It is supposed to work exclusively with the StreamingChatLanguageModel.
     *
     * @param arguments single token, a part of the complete arguments JSON object
     */
    default void onToolArguments(String arguments) {
    }

    /**
     * This method is invoked once LLM has finished responding.
     */
    default void onComplete() {
    }

    /**
     * This method is invoked when an error occurs during streaming.
     *
     * @param error the Throwable error that occurred
     */
    void onError(Throwable error);
}
