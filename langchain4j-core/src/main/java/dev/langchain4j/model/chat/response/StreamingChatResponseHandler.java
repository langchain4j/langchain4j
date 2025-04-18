package dev.langchain4j.model.chat.response;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.StreamingChatModel;

/**
 * TODO review all javadoc in this class
 * Represents a handler for streaming a response from a {@link StreamingChatModel}.
 *
 * @see StreamingChatModel
 */
public interface StreamingChatResponseHandler {

    /**
     * Invoked each time the model generates a partial response (usually a single token) in a textual response.
     * If the model decides to execute a tool instead, this method will not be invoked;
     * {@link #onCompleteResponse} will be invoked instead.
     *
     * @param partialResponse The partial response (usually a single token), which is a part of the complete response.
     */
    void onPartialResponse(String partialResponse);

    /**
     * Invoked each time the model generates a reasoning response (usually a single token) in a textual response.
     * If the model decides to start answering, this method will not be invoked;
     * {@link #onPartialResponse} will be invoked instead.
     *
     * @param reasoningContent The partial response (usually a single token), which is a part of the complete response.
     */
    default void onReasoningResponse(String reasoningContent) {}

    /**
     * Invoked when the model has finished streaming a response.
     * If the model requests the execution of one or multiple tools,
     * this can be accessed via {@link ChatResponse#aiMessage()} -> {@link AiMessage#toolExecutionRequests()}.
     *
     * @param completeResponse The complete response generated by the model.
     *                         For textual responses, it contains all tokens from {@link #onPartialResponse} concatenated.
     */
    void onCompleteResponse(ChatResponse completeResponse);

    /**
     * This method is invoked when an error occurs during streaming.
     *
     * @param error The error that occurred
     */
    void onError(Throwable error);
}
