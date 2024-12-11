package dev.langchain4j.service;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.exception.IllegalConfigurationException;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.service.tool.ToolExecution;
import dev.langchain4j.service.tool.ToolExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static dev.langchain4j.internal.Utils.copyIfNotNull;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.util.Collections.emptyList;

public class AiServiceTokenStream implements TokenStream {

    private final List<ChatMessage> messages;
    private final List<ToolSpecification> toolSpecifications;
    private final Map<String, ToolExecutor> toolExecutors;
    private final List<Content> retrievedContents;
    private final AiServiceContext context;
    private final Object memoryId;

    private Consumer<String> partialResponseHandler;

    private Consumer<List<Content>> contentsHandler;
    private Consumer<ToolExecution> toolExecutionHandler;

    private Consumer<ChatResponse> completeResponseHandler;
    private Consumer<Response<AiMessage>> completionHandler;

    private Consumer<Throwable> errorHandler;

    private int onPartialResponseInvoked;
    private int onNextInvoked;
    private int onCompleteResponseInvoked;
    private int onCompleteInvoked;
    private int onRetrievedInvoked;
    private int onToolExecutedInvoked;
    private int onErrorInvoked;
    private int ignoreErrorsInvoked;

    public AiServiceTokenStream(List<ChatMessage> messages,
                                List<ToolSpecification> toolSpecifications,
                                Map<String, ToolExecutor> toolExecutors,
                                List<Content> retrievedContents,
                                AiServiceContext context,
                                Object memoryId) {
        this.messages = ensureNotEmpty(messages, "messages");
        this.toolSpecifications = copyIfNotNull(toolSpecifications);
        this.toolExecutors = copyIfNotNull(toolExecutors);
        this.retrievedContents = retrievedContents;
        this.context = ensureNotNull(context, "context");
        this.memoryId = ensureNotNull(memoryId, "memoryId");
        ensureNotNull(context.streamingChatModel, "streamingChatModel");
    }

    @Override
    public TokenStream onPartialResponse(Consumer<String> partialResponseHandler) {
        this.partialResponseHandler = partialResponseHandler;
        this.onPartialResponseInvoked++;
        return this;
    }

    @Override
    public TokenStream onNext(Consumer<String> tokenHandler) {
        this.partialResponseHandler = tokenHandler;
        this.onNextInvoked++;
        return this;
    }

    @Override
    public TokenStream onRetrieved(Consumer<List<Content>> contentsHandler) {
        this.contentsHandler = contentsHandler;
        this.onRetrievedInvoked++;
        return this;
    }

    @Override
    public TokenStream onToolExecuted(Consumer<ToolExecution> toolExecutionHandler) {
        this.toolExecutionHandler = toolExecutionHandler;
        this.onToolExecutedInvoked++;
        return this;
    }

    @Override
    public TokenStream onCompleteResponse(Consumer<ChatResponse> completionHandler) {
        this.completeResponseHandler = completionHandler;
        this.onCompleteResponseInvoked++;
        return this;
    }

    @Override
    public TokenStream onComplete(Consumer<Response<AiMessage>> completionHandler) {
        this.completionHandler = completionHandler;
        this.onCompleteInvoked++;
        return this;
    }

    @Override
    public TokenStream onError(Consumer<Throwable> errorHandler) {
        this.errorHandler = errorHandler;
        this.onErrorInvoked++;
        return this;
    }

    @Override
    public TokenStream ignoreErrors() {
        this.errorHandler = null;
        this.ignoreErrorsInvoked++;
        return this;
    }

    @Override
    public void start() {
        validateConfiguration();

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(messages)
                .toolSpecifications(toolSpecifications)
                .build();

        StreamingChatResponseHandler handler = new AiServiceStreamingResponseHandler(
                context,
                memoryId,
                partialResponseHandler,
                toolExecutionHandler,
                completeResponseHandler,
                completionHandler,
                errorHandler,
                initTemporaryMemory(context, messages),
                new TokenUsage(),
                toolSpecifications,
                toolExecutors
        );

        if (contentsHandler != null && retrievedContents != null) {
            contentsHandler.accept(retrievedContents);
        }

        context.streamingChatModel.chat(chatRequest, handler);
    }

    private void validateConfiguration() {
        if (onPartialResponseInvoked + onNextInvoked != 1) {
            throw new IllegalConfigurationException("One of [onPartialResponse, onNext] " +
                    "must be invoked on TokenStream exactly 1 time");
        }
        if (onCompleteResponseInvoked + onCompleteInvoked > 1) {
            throw new IllegalConfigurationException("One of [onCompleteResponse, onComplete] " +
                    "can be invoked on TokenStream at most 1 time");
        }
        if (onRetrievedInvoked > 1) {
            throw new IllegalConfigurationException("onRetrieved can be invoked on TokenStream at most 1 time");
        }
        if (onToolExecutedInvoked > 1) {
            throw new IllegalConfigurationException("onToolExecuted can be invoked on TokenStream at most 1 time");
        }
        if (onErrorInvoked + ignoreErrorsInvoked != 1) {
            throw new IllegalConfigurationException("One of [onError, ignoreErrors] " +
                    "must be invoked on TokenStream exactly 1 time");
        }
    }

    private List<ChatMessage> initTemporaryMemory(AiServiceContext context, List<ChatMessage> messagesToSend) {
        if (context.hasChatMemory()) {
            return emptyList();
        } else {
            return new ArrayList<>(messagesToSend);
        }
    }
}
