package dev.langchain4j.service;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.service.tool.ToolExecution;

import java.util.List;
import java.util.function.Consumer;

/**
 * Represents a token stream from language model to which you can subscribe and receive updates
 * when a new partial response (usually a single token) is available,
 * when language model finishes streaming, or when an error occurs during streaming.
 * It is intended to be used as a return type in AI Service.
 */
public interface TokenStream {

    /**
     * The provided consumer will be invoked every time a new partial response (usually a single token)
     * from a language model is available.
     * <br>
     * This method will replace the {@link #onNext(Consumer)} in the future versions.
     *
     * @param partialResponseHandler lambda that will be invoked when language model generates new partial response
     * @return token stream instance used to configure or start stream processing
     */
    @Experimental
    TokenStream onPartialResponse(Consumer<String> partialResponseHandler);

    /**
     * The provided consumer will be invoked every time a new token from a language model is available.
     * <br>
     * This method will be replaced by the {@link #onPartialResponse(Consumer)} in the future versions.
     *
     * @param tokenHandler lambda that consumes tokens of the response
     * @return token stream instance used to configure or start stream processing
     */
    TokenStream onNext(Consumer<String> tokenHandler);

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
     * The provided consumer will be invoked if any tool is executed.
     * <p>
     * The invocation happens after the tool method has finished and before any other tool is executed.
     *
     * @param toolExecuteHandler lambda that consumes {@link ToolExecution}
     * @return token stream instance used to configure or start stream processing
     */
    TokenStream onToolExecuted(Consumer<ToolExecution> toolExecuteHandler);

    /**
     * The provided handler will be invoked when a language model finishes streaming a response.
     * <br>
     * This method will replace the {@link #onComplete(Consumer)} in the future versions.
     *
     * @param completeResponseHandler lambda that will be invoked when language model finishes streaming
     * @return token stream instance used to configure or start stream processing
     */
    @Experimental
    TokenStream onCompleteResponse(Consumer<ChatResponse> completeResponseHandler);

    /**
     * The provided consumer will be invoked when a language model finishes streaming a response.
     * <br>
     * This method will be replaced by the {@link #onCompleteResponse(Consumer)} in the future versions.
     *
     * @param completionHandler lambda that will be invoked when language model finishes streaming
     * @return token stream instance used to configure or start stream processing
     */
    TokenStream onComplete(Consumer<Response<AiMessage>> completionHandler);

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
     * Completes the current token stream building and starts processing.
     * <p>
     * Will send a request to LLM and start response streaming.
     */
    void start();
}
