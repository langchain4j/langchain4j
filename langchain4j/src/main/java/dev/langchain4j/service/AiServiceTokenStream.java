package dev.langchain4j.service;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.service.AiServiceParamsUtil.chatRequestParameters;

import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.guardrail.ChatExecutor;
import dev.langchain4j.guardrail.GuardrailRequestParams;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.PartialResponseContext;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.PartialThinkingContext;
import dev.langchain4j.model.chat.response.PartialToolCall;
import dev.langchain4j.model.chat.response.PartialToolCallContext;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.service.tool.BeforeToolExecution;
import dev.langchain4j.service.tool.ToolArgumentsErrorHandler;
import dev.langchain4j.service.tool.ToolExecution;
import dev.langchain4j.service.tool.ToolExecutionErrorHandler;
import dev.langchain4j.service.tool.ToolExecutor;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@Internal
public class AiServiceTokenStream implements TokenStream {

    private final List<ChatMessage> messages;

    private final List<ToolSpecification> toolSpecifications;
    private final Map<String, ToolExecutor> toolExecutors;
    private final ToolArgumentsErrorHandler toolArgumentsErrorHandler;
    private final ToolExecutionErrorHandler toolExecutionErrorHandler;
    private final Executor toolExecutor;

    private final List<Content> retrievedContents;
    private final AiServiceContext context;
    private final InvocationContext invocationContext;
    private final GuardrailRequestParams commonGuardrailParams;
    private final Object methodKey;

    private Consumer<String> partialResponseHandler;
    private BiConsumer<PartialResponse, PartialResponseContext> partialResponseWithContextHandler;
    private Consumer<PartialThinking> partialThinkingHandler;
    private BiConsumer<PartialThinking, PartialThinkingContext> partialThinkingWithContextHandler;
    private Consumer<PartialToolCall> partialToolCallHandler;
    private BiConsumer<PartialToolCall, PartialToolCallContext> partialToolCallWithContextHandler;
    private Consumer<List<Content>> contentsHandler;
    private Consumer<ChatResponse> intermediateResponseHandler;
    private Consumer<BeforeToolExecution> beforeToolExecutionHandler;
    private Consumer<ToolExecution> toolExecutionHandler;
    private Consumer<ChatResponse> completeResponseHandler;
    private Consumer<Throwable> errorHandler;

    private int onPartialResponseInvoked;
    private int onPartialResponseWithContextInvoked;
    private int onPartialThinkingInvoked;
    private int onPartialThinkingWithContextInvoked;
    private int onPartialToolCallInvoked;
    private int onPartialToolCallWithContextInvoked;
    private int onIntermediateResponseInvoked;
    private int onCompleteResponseInvoked;
    private int onRetrievedInvoked;
    private int beforeToolExecutionInvoked;
    private int onToolExecutedInvoked;
    private int onErrorInvoked;
    private int ignoreErrorsInvoked;

    /**
     * Creates a new instance of {@link AiServiceTokenStream} with the given parameters.
     *
     * @param parameters the parameters for creating the token stream
     */
    public AiServiceTokenStream(AiServiceTokenStreamParameters parameters) {
        ensureNotNull(parameters, "parameters");
        this.messages = copy(ensureNotEmpty(parameters.messages(), "messages"));
        this.toolSpecifications = copy(parameters.toolSpecifications());
        this.toolExecutors = copy(parameters.toolExecutors());
        this.toolArgumentsErrorHandler = parameters.toolArgumentsErrorHandler();
        this.toolExecutionErrorHandler = parameters.toolExecutionErrorHandler();
        this.toolExecutor = parameters.toolExecutor();
        this.retrievedContents = copy(parameters.gretrievedContents());
        this.context = ensureNotNull(parameters.context(), "context");
        ensureNotNull(this.context.streamingChatModel, "streamingChatModel");
        this.invocationContext = parameters.invocationContext();
        this.commonGuardrailParams = parameters.commonGuardrailParams();
        this.methodKey = parameters.methodKey();
    }

    @Override
    public TokenStream onPartialResponse(Consumer<String> partialResponseHandler) {
        this.partialResponseHandler = partialResponseHandler;
        this.onPartialResponseInvoked++;
        return this;
    }

    @Override
    public TokenStream onPartialResponseWithContext(BiConsumer<PartialResponse, PartialResponseContext> handler) {
        this.partialResponseWithContextHandler = handler;
        this.onPartialResponseWithContextInvoked++;
        return this;
    }

    @Override
    public TokenStream onPartialThinking(Consumer<PartialThinking> partialThinkingHandler) {
        this.partialThinkingHandler = partialThinkingHandler;
        this.onPartialThinkingInvoked++;
        return this;
    }

    @Override
    public TokenStream onPartialThinkingWithContext(BiConsumer<PartialThinking, PartialThinkingContext> handler) {
        this.partialThinkingWithContextHandler = handler;
        this.onPartialThinkingWithContextInvoked++;
        return this;
    }

    @Override
    public TokenStream onPartialToolCall(Consumer<PartialToolCall> partialToolCallHandler) {
        this.partialToolCallHandler = partialToolCallHandler;
        this.onPartialToolCallInvoked++;
        return this;
    }

    @Override
    public TokenStream onPartialToolCall(BiConsumer<PartialToolCall, PartialToolCallContext> handler) {
        this.partialToolCallWithContextHandler = handler;
        this.onPartialToolCallWithContextInvoked++;
        return this;
    }

    @Override
    public TokenStream onRetrieved(Consumer<List<Content>> contentsHandler) {
        this.contentsHandler = contentsHandler;
        this.onRetrievedInvoked++;
        return this;
    }

    @Override
    public TokenStream onIntermediateResponse(Consumer<ChatResponse> intermediateResponseHandler) {
        this.intermediateResponseHandler = intermediateResponseHandler;
        this.onIntermediateResponseInvoked++;
        return this;
    }

    @Override
    public TokenStream beforeToolExecution(Consumer<BeforeToolExecution> beforeToolExecutionHandler) {
        this.beforeToolExecutionHandler = beforeToolExecutionHandler;
        this.beforeToolExecutionInvoked++;
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

        ChatRequest chatRequest = context.chatRequestTransformer.apply(
                ChatRequest.builder()
                        .messages(messages)
                        .parameters(chatRequestParameters(invocationContext.methodArguments(), toolSpecifications))
                        .build(),
                invocationContext.chatMemoryId());

        ChatExecutor chatExecutor = ChatExecutor.builder(context.streamingChatModel)
                .errorHandler(errorHandler)
                .chatRequest(chatRequest)
                .build();

        var handler = new AiServiceStreamingResponseHandler(
                chatRequest,
                chatExecutor,
                context,
                invocationContext,
                partialResponseHandler,
                partialResponseWithContextHandler,
                partialThinkingHandler,
                partialThinkingWithContextHandler,
                partialToolCallHandler,
                partialToolCallWithContextHandler,
                beforeToolExecutionHandler,
                toolExecutionHandler,
                intermediateResponseHandler,
                completeResponseHandler,
                errorHandler,
                initTemporaryMemory(context, messages),
                new TokenUsage(),
                toolSpecifications,
                toolExecutors,
                context.toolService.maxSequentialToolsInvocations(),
                toolArgumentsErrorHandler,
                toolExecutionErrorHandler,
                toolExecutor,
                commonGuardrailParams,
                methodKey);

        if (contentsHandler != null && retrievedContents != null) {
            contentsHandler.accept(retrievedContents);
        }

        context.streamingChatModel.chat(chatRequest, handler);
    }

    private void validateConfiguration() {
        if (onPartialResponseInvoked + onPartialResponseWithContextInvoked > 1) {
            throw new IllegalConfigurationException("One of [onPartialResponse, onPartialResponseWithContext] "
                    + "can be invoked on TokenStream at most 1 time");
        }
        if (onPartialThinkingInvoked + onPartialThinkingWithContextInvoked > 1) {
            throw new IllegalConfigurationException("One of [onPartialThinking, onPartialThinkingWithContext] "
                    + "can be invoked on TokenStream at most 1 time");
        }
        if (onPartialToolCallInvoked + onPartialToolCallWithContextInvoked > 1) {
            throw new IllegalConfigurationException("onPartialToolCall can be invoked on TokenStream at most 1 time");
        }
        if (onIntermediateResponseInvoked > 1) {
            throw new IllegalConfigurationException(
                    "onIntermediateResponse can be invoked on TokenStream at most 1 time");
        }
        if (onCompleteResponseInvoked > 1) {
            throw new IllegalConfigurationException("onCompleteResponse can be invoked on TokenStream at most 1 time");
        }
        if (onRetrievedInvoked > 1) {
            throw new IllegalConfigurationException("onRetrieved can be invoked on TokenStream at most 1 time");
        }
        if (beforeToolExecutionInvoked > 1) {
            throw new IllegalConfigurationException("beforeToolExecution can be invoked on TokenStream at most 1 time");
        }
        if (onToolExecutedInvoked > 1) {
            throw new IllegalConfigurationException("onToolExecuted can be invoked on TokenStream at most 1 time");
        }
        if (onErrorInvoked + ignoreErrorsInvoked != 1) {
            throw new IllegalConfigurationException(
                    "One of [onError, ignoreErrors] " + "must be invoked on TokenStream exactly 1 time");
        }
    }

    private ChatMemory initTemporaryMemory(AiServiceContext context, List<ChatMessage> messagesToSend) {
        var chatMemory = MessageWindowChatMemory.withMaxMessages(Integer.MAX_VALUE);

        if (!context.hasChatMemory()) {
            chatMemory.add(messagesToSend);
        }

        return chatMemory;
    }
}
