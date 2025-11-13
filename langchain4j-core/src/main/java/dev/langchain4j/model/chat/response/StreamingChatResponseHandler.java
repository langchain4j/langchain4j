package dev.langchain4j.model.chat.response;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.chat.StreamingChatModel;

/**
 * Represents a handler for a {@link StreamingChatModel} response.
 *
 * @see StreamingChatModel
 */
public interface StreamingChatResponseHandler {

    /**
     * Invoked each time the model generates a partial textual response, usually a single token.
     * <p>
     * Please note that some LLM providers do not stream individual tokens, but send responses in batches.
     * In such cases, this callback may receive multiple tokens at once.
     * <p>
     * Either this or the {@link #onPartialResponse(PartialResponse, PartialResponseContext)} method
     * should be implemented if you want to consume tokens as soon as they become available.
     *
     * @param partialResponse A partial textual response, usually a single token.
     * @see #onPartialResponse(PartialResponse, PartialResponseContext)
     */
    default void onPartialResponse(String partialResponse) {}

    /**
     * Invoked each time the model generates a partial textual response, usually a single token.
     * <p>
     * Please note that some LLM providers do not stream individual tokens, but send responses in batches.
     * In such cases, this callback may receive multiple tokens at once.
     * <p>
     * Either this or the {@link #onPartialResponse(String)} method
     * should be implemented if you want to consume tokens as soon as they become available.
     *
     * @param partialResponse A partial textual response, usually a single token.
     * @param context         A partial response context.
     *                        Contains a {@link StreamingHandle} that can be used to cancel streaming.
     * @see #onPartialResponse(String)
     * @since 1.8.0
     */
    @Experimental
    default void onPartialResponse(PartialResponse partialResponse, PartialResponseContext context) {
        onPartialResponse(partialResponse.text());
    }

    /**
     * Invoked each time the model generates a partial thinking/reasoning text, usually a single token.
     * <p>
     * Please note that some LLM providers do not stream individual tokens, but send thinking tokens in batches.
     * In such cases, this callback may receive multiple tokens at once.
     * <p>
     * Either this or the {@link #onPartialThinking(PartialThinking, PartialThinkingContext)} method
     * should be implemented if you want to consume thinking tokens as soon as they become available.
     *
     * @param partialThinking A partial thinking text, usually a single token.
     * @see #onPartialThinking(PartialThinking, PartialThinkingContext)
     * @since 1.2.0
     */
    @Experimental
    default void onPartialThinking(PartialThinking partialThinking) {}

    /**
     * Invoked each time the model generates a partial thinking/reasoning text, usually a single token.
     * <p>
     * Please note that some LLM providers do not stream individual tokens, but send thinking tokens in batches.
     * In such cases, this callback may receive multiple tokens at once.
     * <p>
     * Either this or the {@link #onPartialThinking(PartialThinking)} method
     * should be implemented if you want to consume thinking tokens as soon as they become available.
     *
     * @param partialThinking A partial thinking text, usually a single token.
     * @param context         A partial thinking context.
     *                        Contains a {@link StreamingHandle} that can be used to cancel streaming.
     * @see #onPartialThinking(PartialThinking)
     * @since 1.8.0
     */
    @Experimental
    default void onPartialThinking(PartialThinking partialThinking, PartialThinkingContext context) {
        onPartialThinking(partialThinking);
    }

    /**
     * This callback is invoked each time the model generates a partial tool call,
     * which contains a single token of the tool's arguments.
     * It is typically invoked multiple times for a single tool call
     * until {@link #onCompleteToolCall(CompleteToolCall)} is eventually invoked,
     * indicating that the streaming for that tool call is finished.
     * <p>
     * Here's an example of what streaming a single tool call might look like:
     * <pre>
     * 1. onPartialToolCall(index = 0, id = "call_abc", name = "get_weather", partialArguments = "{\"")
     * 2. onPartialToolCall(index = 0, id = "call_abc", name = "get_weather", partialArguments = "city")
     * 3. onPartialToolCall(index = 0, id = "call_abc", name = "get_weather", partialArguments = ""\":\"")
     * 4. onPartialToolCall(index = 0, id = "call_abc", name = "get_weather", partialArguments = "Mun")
     * 5. onPartialToolCall(index = 0, id = "call_abc", name = "get_weather", partialArguments = "ich")
     * 6. onPartialToolCall(index = 0, id = "call_abc", name = "get_weather", partialArguments = "\"}")
     * 7. onCompleteToolCall(index = 0, id = "call_abc", name = "get_weather", arguments = "{\"city\":\"Munich\"}")
     * </pre>
     * <p>
     * If the model decides to call multiple tools, the index will increment, allowing you to correlate.
     * <p>
     * Please note that not all LLM providers stream tool calls token by token.
     * Some providers (e.g., Bedrock, Google, Mistral, Ollama) return only complete tool calls.
     * In those cases, this callback won't be invoked - only {@link #onCompleteToolCall(CompleteToolCall)}
     * will be called.
     * <p>
     * Either this or the {@link #onPartialToolCall(PartialToolCall, PartialToolCallContext)} method
     * should be implemented if you want to consume partial tool calls as soon as they become available.
     *
     * @param partialToolCall A partial tool call that contains
     *                        the index, tool ID, tool name and partial arguments.
     * @see #onPartialToolCall(PartialToolCall, PartialToolCallContext)
     * @since 1.2.0
     */
    @Experimental
    default void onPartialToolCall(PartialToolCall partialToolCall) {}

    /**
     * This callback is invoked each time the model generates a partial tool call,
     * which contains a single token of the tool's arguments.
     * It is typically invoked multiple times for a single tool call
     * until {@link #onCompleteToolCall(CompleteToolCall)} is eventually invoked,
     * indicating that the streaming for that tool call is finished.
     * <p>
     * Here's an example of what streaming a single tool call might look like:
     * <pre>
     * 1. onPartialToolCall(index = 0, id = "call_abc", name = "get_weather", partialArguments = "{\"")
     * 2. onPartialToolCall(index = 0, id = "call_abc", name = "get_weather", partialArguments = "city")
     * 3. onPartialToolCall(index = 0, id = "call_abc", name = "get_weather", partialArguments = ""\":\"")
     * 4. onPartialToolCall(index = 0, id = "call_abc", name = "get_weather", partialArguments = "Mun")
     * 5. onPartialToolCall(index = 0, id = "call_abc", name = "get_weather", partialArguments = "ich")
     * 6. onPartialToolCall(index = 0, id = "call_abc", name = "get_weather", partialArguments = "\"}")
     * 7. onCompleteToolCall(index = 0, id = "call_abc", name = "get_weather", arguments = "{\"city\":\"Munich\"}")
     * </pre>
     * <p>
     * If the model decides to call multiple tools, the index will increment, allowing you to correlate.
     * <p>
     * Please note that not all LLM providers stream tool calls token by token.
     * Some providers (e.g., Bedrock, Google, Mistral, Ollama) return only complete tool calls.
     * In those cases, this callback won't be invoked - only {@link #onCompleteToolCall(CompleteToolCall)}
     * will be called.
     * <p>
     * Either this or the {@link #onPartialToolCall(PartialToolCall)} method
     * should be implemented if you want to consume partial tool calls as soon as they become available.
     *
     * @param partialToolCall A partial tool call that contains
     *                        the index, tool ID, tool name and partial arguments.
     * @param context         A partial tool call context.
     *                        Contains a {@link StreamingHandle} that can be used to cancel streaming.
     * @see #onPartialToolCall(PartialToolCall)
     * @since 1.8.0
     */
    @Experimental
    default void onPartialToolCall(PartialToolCall partialToolCall, PartialToolCallContext context) {
        onPartialToolCall(partialToolCall);
    }

    /**
     * Invoked when the model has finished streaming a single tool call.
     *
     * @param completeToolCall A complete tool call that contains
     *                         the index, tool ID, tool name, and fully assembled arguments.
     * @since 1.2.0
     */
    @Experimental
    default void onCompleteToolCall(CompleteToolCall completeToolCall) {}

    /**
     * Invoked when the model has finished streaming a response.
     *
     * @param completeResponse The complete response generated by the model,
     *                         containing all assembled partial text and tool calls.
     */
    void onCompleteResponse(ChatResponse completeResponse);

    /**
     * This method is invoked when an error occurs during streaming.
     *
     * @param error The error that occurred
     */
    void onError(Throwable error);
}
