package dev.langchain4j.service;

import dev.langchain4j.Experimental;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.PartialResponseContext;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.PartialThinkingContext;
import dev.langchain4j.model.chat.response.PartialToolCall;
import dev.langchain4j.model.chat.response.PartialToolCallContext;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.service.tool.BeforeToolExecution;
import dev.langchain4j.service.tool.ToolExecution;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Represents a token stream from the model to which you can subscribe and receive updates
 * when a new partial response (usually a single token) is available,
 *  when the model finishes streaming, or when an error occurs during streaming.
 * It is intended to be used as a return type in AI Service.
 */
public interface TokenStream {

    /**
     * The provided consumer will be invoked every time a new partial textual response (usually a single token)
     * from a language model is available.
     * <p>
     * Either this or the {@link #onPartialResponseWithContext(BiConsumer)} callback can be used
     * if you want to consume tokens as soon as they become available.
     *
     * @param partialResponseHandler lambda that will be invoked when a model generates a new partial textual response
     * @return token stream instance used to configure or start stream processing
     * @see #onPartialResponseWithContext(BiConsumer)
     */
    TokenStream onPartialResponse(Consumer<String> partialResponseHandler);

    /**
     * The provided consumer will be invoked every time a new partial textual response (usually a single token)
     * from a language model is available.
     * <p>
     * Either this or the {@link #onPartialResponse(Consumer)} callback can be used
     * if you want to consume tokens as soon as they become available.
     *
     * @param handler lambda that will be invoked when a model generates a new partial textual response
     * @return token stream instance used to configure or start stream processing
     * @see #onPartialResponse(Consumer)
     * @since 1.8.0
     */
    @Experimental
    default TokenStream onPartialResponseWithContext(BiConsumer<PartialResponse, PartialResponseContext> handler) {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * The provided consumer will be invoked every time a new partial thinking/reasoning text (usually a single token)
     * from a language model is available.
     * <p>
     * Either this or the {@link #onPartialThinkingWithContext(BiConsumer)} callback can be used
     * if you want to consume thinking tokens as soon as they become available.
     *
     * @param partialThinkingHandler lambda that will be invoked when a model generates a new partial thinking/reasoning text
     * @return token stream instance used to configure or start stream processing
     * @see #onPartialThinkingWithContext(BiConsumer)
     * @since 1.2.0
     */
    @Experimental
    default TokenStream onPartialThinking(Consumer<PartialThinking> partialThinkingHandler) {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * The provided consumer will be invoked every time a new partial thinking/reasoning text (usually a single token)
     * from a language model is available.
     * <p>
     * Either this or the {@link #onPartialThinking(Consumer)} callback can be used
     * if you want to consume thinking tokens as soon as they become available.
     *
     * @param handler lambda that will be invoked when a model generates a new partial thinking/reasoning text
     * @return token stream instance used to configure or start stream processing
     * @see #onPartialThinking(Consumer)
     * @since 1.8.0
     */
    @Experimental
    default TokenStream onPartialThinkingWithContext(BiConsumer<PartialThinking, PartialThinkingContext> handler) {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * The provided consumer will be invoked every time a new partial tool call
     * (usually containing a single token of the tool's arguments) from a language model is available.
     * <p>
     * Either this or the {@link #onPartialToolCallWithContext(BiConsumer)} callback can be used
     * if you want to consume partial tool calls as soon as they become available.
     *
     * @param partialToolCallHandler lambda that will be invoked when a model generates a new partial tool call
     * @return token stream instance used to configure or start stream processing
     * @see #onPartialToolCallWithContext(BiConsumer)
     * @since 1.11.0
     */
    @Experimental
    default TokenStream onPartialToolCall(Consumer<PartialToolCall> partialToolCallHandler) {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * The provided consumer will be invoked every time a new partial tool call
     * (usually containing a single token of the tool's arguments) from a language model is available.
     * <p>
     * Either this or the {@link #onPartialToolCall(Consumer)} callback can be used
     * if you want to consume partial tool calls as soon as they become available.
     *
     * @param handler lambda that will be invoked when a model generates a new partial tool call
     * @return token stream instance used to configure or start stream processing
     * @see #onPartialToolCall(Consumer)
     * @since 1.11.0
     */
    @Experimental
    default TokenStream onPartialToolCallWithContext(BiConsumer<PartialToolCall, PartialToolCallContext> handler) {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * The provided consumer will be invoked if any {@link Content}s are retrieved using {@link RetrievalAugmentor}.
     * <p>
     * The invocation happens before any call is made to the language model.
     *
     * @param contentHandler lambda that consumes all retrieved contents
     * @return token stream instance used to configure or start stream processing
     */
    TokenStream onRetrieved(Consumer<List<Content>> contentHandler);

    /**
     * The provided consumer will be invoked when a language model finishes streaming the <i>intermediate</i> chat response,
     * as opposed to the final response (see {@link #onCompleteResponse(Consumer)}).
     * Intermediate chat responses contain {@link ToolExecutionRequest}s, AI service will execute them
     * after returning from this consumer.
     *
     * @param intermediateResponseHandler lambda that consumes intermediate chat responses
     * @return token stream instance used to configure or start stream processing
     * @see #onCompleteResponse(Consumer)
     * @since 1.2.0
     */
    default TokenStream onIntermediateResponse(Consumer<ChatResponse> intermediateResponseHandler) {
        throw new UnsupportedOperationException("Consuming intermediate responses is not supported "
                + "by this implementation of TokenStream: " + this.getClass().getName());
    }

    /**
     * The provided consumer will be invoked right before a tool is executed.
     * <p>
     * In streaming mode this callback is ordered after {@link #onIntermediateResponse(Consumer)}
     * for the response that requested the tool. Since this callback can throw and veto the tool
     * body, registering it disables eager tool execution before the intermediate response is
     * delivered.
     *
     * @param beforeToolExecutionHandler lambda that consumes {@link BeforeToolExecution}
     * @return token stream instance used to configure or start stream processing
     * @since 1.2.0
     */
    default TokenStream beforeToolExecution(Consumer<BeforeToolExecution> beforeToolExecutionHandler) {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * The provided consumer will be invoked right after a tool is executed.
     * <p>
     * In streaming mode, this callback is delivered after {@link #onIntermediateResponse(Consumer)}
     * for the response that requested the tool.
     *
     * @param toolExecuteHandler lambda that consumes {@link ToolExecution}
     * @return token stream instance used to configure or start stream processing
     */
    TokenStream onToolExecuted(Consumer<ToolExecution> toolExecuteHandler);

    /**
     * The provided consumer will be invoked when a language model finishes streaming the <i>final</i> chat response,
     * as opposed to the intermediate response (see {@link #onIntermediateResponse(Consumer)}).
     * <p>
     * Please note that {@link ChatResponse#tokenUsage()} contains aggregate token usage across all calls to the LLM.
     * It is a sum of {@link ChatResponse#tokenUsage()}s of all intermediate responses
     * ({@link #onIntermediateResponse(Consumer)}).
     *
     * @param completeResponseHandler lambda that will be invoked when language model finishes streaming
     * @return token stream instance used to configure or start stream processing
     * @see #onIntermediateResponse(Consumer)
     */
    TokenStream onCompleteResponse(Consumer<ChatResponse> completeResponseHandler);

    /**
     * The provided consumer will be invoked when an error occurs during streaming.
     *
     * @param errorHandler lambda that will be invoked when an error occurs
     * @return token stream instance used to configure or start stream processing
     */
    TokenStream onError(Consumer<Throwable> errorHandler);

    /**
     * All errors during streaming will be ignored (but will be logged with a WARN log level).
     *
     * @return token stream instance used to configure or start stream processing
     */
    TokenStream ignoreErrors();

    /**
     * Registers a per-stream cancellation flag, polled at agent-loop checkpoints. When the supplier
     * returns {@code true}, the loop cancels pending tool futures, writes placeholder tool-result
     * messages for any in-flight tool requests (preserving the tool_use/tool_result memory
     * invariant), and skips both the assistant-turn memory write and the follow-up chat call.
     * Cancellation is silent: no consumer callback fires.
     *
     * @param cancellationSupplier supplier returning {@code true} to cancel
     * @return this token stream
     * @since 1.15.0-beta25
     */
    @Experimental
    default TokenStream cancelOn(Supplier<Boolean> cancellationSupplier) {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * Completes the current token stream building and starts processing.
     * <p>
     * Will send a request to LLM and start response streaming.
     */
    void start();
}
