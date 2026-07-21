package dev.langchain4j.service;

import dev.langchain4j.exception.AsyncNotSupportedException;
import static dev.langchain4j.agent.tool.ReturnBehavior.IMMEDIATE;
import static dev.langchain4j.agent.tool.ReturnBehavior.IMMEDIATE_IF_LAST;
import static dev.langchain4j.internal.CompletableFutureUtils.propagateCancellation;
import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static dev.langchain4j.internal.Exceptions.unwrapCompletionException;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureGreaterThanZero;
import static dev.langchain4j.model.chat.Capability.RESPONSE_FORMAT_JSON_SCHEMA;
import static dev.langchain4j.model.chat.request.ResponseFormatType.JSON;
import static dev.langchain4j.model.output.FinishReason.TOOL_EXECUTION;
import static dev.langchain4j.service.AiServiceParamsUtil.chatRequestParameters;
import static dev.langchain4j.service.AiServiceParamsUtil.findArgumentOfType;
import static dev.langchain4j.service.AiServiceValidation.validateParameters;
import static dev.langchain4j.service.IllegalConfigurationException.illegalConfiguration;
import static dev.langchain4j.service.TypeUtils.getRawClass;
import static dev.langchain4j.service.TypeUtils.isImageType;
import static dev.langchain4j.service.TypeUtils.resolveFirstGenericParameterClass;
import static dev.langchain4j.service.TypeUtils.resolveFirstGenericParameterType;
import static dev.langchain4j.service.TypeUtils.typeHasRawClass;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;

import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.ReturnBehavior;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.guardrail.ChatExecutor;
import dev.langchain4j.guardrail.GuardrailRequestParams;
import dev.langchain4j.guardrail.InputGuardrailRequest;
import dev.langchain4j.guardrail.OutputGuardrailRequest;
import dev.langchain4j.internal.DefaultExecutorProvider;
import dev.langchain4j.internal.InternalFlowUtils;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.invocation.LangChain4jManaged;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.model.moderation.Moderation;
import dev.langchain4j.observability.api.event.AiServiceCompletedEvent;
import dev.langchain4j.observability.api.event.AiServiceErrorEvent;
import dev.langchain4j.observability.api.event.AiServiceRequestIssuedEvent;
import dev.langchain4j.observability.api.event.AiServiceResponseReceivedEvent;
import dev.langchain4j.observability.api.event.AiServiceStartedEvent;
import dev.langchain4j.rag.AugmentationRequest;
import dev.langchain4j.rag.AugmentationResult;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.query.Metadata;
import dev.langchain4j.service.guardrail.GuardrailService;
import dev.langchain4j.service.memory.ChatMemoryAccess;
import dev.langchain4j.service.memory.ChatMemoryService;
import dev.langchain4j.service.output.ServiceOutputParser;
import dev.langchain4j.service.tool.ToolExecution;
import dev.langchain4j.service.tool.ToolServiceContext;
import dev.langchain4j.service.tool.ToolServiceResult;
import dev.langchain4j.spi.services.CompletableFutureAdapter;
import dev.langchain4j.spi.services.PublisherAdapter;
import dev.langchain4j.spi.services.TokenStreamAdapter;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.stream.Collectors;

@Internal
class DefaultAiServices<T> extends AiServices<T> {

    private final ServiceOutputParser serviceOutputParser = new ServiceOutputParser();
    private final Collection<TokenStreamAdapter> tokenStreamAdapters = loadFactories(TokenStreamAdapter.class);
    private final Collection<CompletableFutureAdapter> completableFutureAdapters =
            loadFactories(CompletableFutureAdapter.class);
    private final Collection<PublisherAdapter> publisherAdapters = loadFactories(PublisherAdapter.class);

    private static final Set<Class<? extends Annotation>> VALID_PARAM_ANNOTATIONS =
            Set.of(dev.langchain4j.service.UserMessage.class, V.class, MemoryId.class, UserName.class);

    // Used to satisfy the Reactive Streams contract (onError must be preceded by onSubscribe) when the reactive
    // publisher fails before any real subscription exists - e.g. if the deferred chat-memory assembly fails.
    private static final Flow.Subscription NOOP_SUBSCRIPTION = InternalFlowUtils.EMPTY_SUBSCRIPTION;

    DefaultAiServices(AiServiceContext context) {
        super(context);
    }

    protected void validate() {
        performBasicValidation();
        AiServiceValidation.validate(context);
    }

    private Object handleChatMemoryAccess(Method method, Object[] args) {
        return switch (method.getName()) {
            case "getChatMemory" -> context.chatMemoryService.getChatMemory(args[0]);
            case "evictChatMemory" -> context.chatMemoryService.evictChatMemory(args[0]) != null;
            default ->
                throw new UnsupportedOperationException(
                        "Unknown method on ChatMemoryAccess class : " + method.getName());
        };
    }

    public T build() {
        validate();

        context.streamingBufferSize = ensureGreaterThanZero(
                getOrDefault(context.streamingBufferSize, AiServiceStreamingEventPublisher.DEFAULT_BUFFER_SIZE),
                "streamingBufferSize");

        Object proxyInstance = Proxy.newProxyInstance(
                context.aiServiceClass.getClassLoader(),
                new Class<?>[] {context.aiServiceClass},
                new InvocationHandler() {

                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        if (method.isDefault()) {
                            return InvocationHandler.invokeDefault(proxy, method, args);
                        }

                        if (method.getDeclaringClass() == Object.class) {
                            switch (method.getName()) {
                                case "equals":
                                    return proxy == args[0];
                                case "hashCode":
                                    return System.identityHashCode(proxy);
                                case "toString":
                                    return context.aiServiceClass.getName() + "@"
                                            + Integer.toHexString(System.identityHashCode(proxy));
                                default:
                                    throw new IllegalStateException("Unexpected Object method: " + method);
                            }
                        }

                        if (method.getDeclaringClass() == ChatMemoryAccess.class) {
                            return handleChatMemoryAccess(method, args);
                        }

                        // TODO do it once, when creating AI Service?
                        validateParameters(context.aiServiceClass, method);

                        InvocationParameters invocationParameters = findArgumentOfType(
                                        InvocationParameters.class, args, method.getParameters())
                                .orElseGet(InvocationParameters::new);

                        InvocationContext invocationContext = InvocationContext.builder()
                                .invocationId(UUID.randomUUID())
                                .interfaceName(context.aiServiceClass.getName())
                                .methodName(method.getName())
                                .methodArguments(args != null ? Arrays.asList(args) : List.of())
                                .chatMemoryId(findMemoryId(method, args).orElse(ChatMemoryService.DEFAULT))
                                .defaultRequestParameters(determineChatRequestParameters(context))
                                .modelProvider(determineModelProvider(context))
                                .invocationParameters(invocationParameters)
                                .managedParameters(LangChain4jManaged.current())
                                .timestampNow()
                                .build();
                        try {
                            return invoke(method, args, invocationContext);
                        } catch (Exception ex) {
                            context.eventListenerRegistrar.fireEvent(AiServiceErrorEvent.builder()
                                    .invocationContext(invocationContext)
                                    .error(ex)
                                    .build());
                            Optional<Object> asyncFailure = asAsyncOrReactiveFailure(method, ex);
                            if (asyncFailure.isPresent()) {
                                return asyncFailure.get();
                            }
                            throw ex;
                        }
                    }

                    /**
                     * If {@code method} returns an asynchronous ({@code CompletableFuture}/{@code CompletionStage} or an
                     * adapted type) or reactive ({@code Flow.Publisher} or an adapted type) value, wraps {@code error}
                     * into a matching already-failed future / immediately-failing publisher so a synchronous prologue
                     * failure is delivered through the returned value rather than thrown. Returns empty for synchronous
                     * return types (the caller then rethrows) and for a reactive stream of an unsupported element type
                     * (a configuration error, left to surface synchronously).
                     */
                    private Optional<Object> asAsyncOrReactiveFailure(Method method, Throwable error) {
                        if (error instanceof IllegalConfigurationException) {
                            return Optional.empty();
                        }
                        Type declaredReturnType =
                                context.returnType != null ? context.returnType : method.getGenericReturnType();
                        CompletableFutureAdapter completableFutureAdapter =
                                findCompletableFutureAdapter(declaredReturnType);
                        boolean asyncReturnType = typeHasRawClass(declaredReturnType, CompletableFuture.class)
                                || typeHasRawClass(declaredReturnType, CompletionStage.class)
                                || completableFutureAdapter != null;
                        Type returnType =
                                asyncReturnType ? resolveFirstGenericParameterType(declaredReturnType) : declaredReturnType;

                        if (asyncReturnType) {
                            CompletableFuture<Object> failed = new CompletableFuture<>();
                            failed.completeExceptionally(error);
                            return Optional.of(completableFutureAdapter != null
                                    ? completableFutureAdapter.fromCompletableFuture(declaredReturnType, failed)
                                    : failed);
                        }

                        PublisherAdapter publisherAdapter = findPublisherAdapter(returnType);
                        boolean reactiveStreaming =
                                typeHasRawClass(returnType, Flow.Publisher.class) || publisherAdapter != null;
                        if (reactiveStreaming) {
                            Type elementType = resolveFirstGenericParameterType(returnType);
                            if (elementType != AiServiceStreamingEvent.class && elementType != String.class) {
                                // Unsupported element type is a configuration error; let it surface synchronously.
                                return Optional.empty();
                            }
                            Flow.Publisher<AiServiceStreamingEvent> failingEvents = subscriber -> {
                                subscriber.onSubscribe(NOOP_SUBSCRIPTION);
                                subscriber.onError(error);
                            };
                            Flow.Publisher<?> mapped = elementType == AiServiceStreamingEvent.class
                                    ? failingEvents
                                    : AiServiceStreamingEventPublisher.toTextPublisher(
                                            failingEvents,
                                            context.guardrailService().hasOutputGuardrails(method),
                                            context.streamingBufferSize);
                            return Optional.of(
                                    publisherAdapter != null ? publisherAdapter.fromPublisher(returnType, mapped) : mapped);
                        }

                        return Optional.empty();
                    }

                    private static ChatRequestParameters determineChatRequestParameters(AiServiceContext context) {
                        if (context.chatModel != null) {
                            return context.chatModel.defaultRequestParameters();
                        }
                        return context.streamingChatModel != null
                                ? context.streamingChatModel.defaultRequestParameters()
                                : null;
                    }

                    private static ModelProvider determineModelProvider(AiServiceContext context) {
                        if (context.chatModel != null) {
                            return context.chatModel.provider();
                        }
                        return context.streamingChatModel != null ? context.streamingChatModel.provider() : null;
                    }

                    public Object invoke(Method method, Object[] args, InvocationContext invocationContext) {

                        Object memoryId = invocationContext.chatMemoryId();
                        ChatMemory chatMemory = context.hasChatMemory()
                                ? context.chatMemoryService.getOrCreateChatMemory(memoryId)
                                : null;

                        Optional<SystemMessage> systemMessage = prepareSystemMessage(invocationContext, method, args);
                        if (context.systemMessageTransformer != null) {
                            String transformedSystemMessage = context.systemMessageTransformer.apply(
                                    systemMessage.map(SystemMessage::text).orElse(null), invocationContext);
                            systemMessage = transformedSystemMessage != null
                                    ? Optional.of(SystemMessage.from(transformedSystemMessage))
                                    : Optional.empty();
                        }
                        var userMessageTemplate = getUserMessageTemplate(memoryId, method, args);
                        var variables = InternalReflectionVariableResolver.findTemplateVariables(
                                userMessageTemplate, method, args);
                        UserMessage originalUserMessage =
                                prepareUserMessage(method, args, userMessageTemplate, variables);

                        context.eventListenerRegistrar.fireEvent(AiServiceStartedEvent.builder()
                                .invocationContext(invocationContext)
                                .systemMessage(systemMessage)
                                .userMessage(originalUserMessage)
                                .build());

                        // RAG augmentation is performed per return-type mode below: on the calling thread for the
                        // synchronous path, and off the calling thread (composed into the result) for the asynchronous
                        // and reactive paths - see augmentAsyncIfNeeded.

                        Type declaredReturnType =
                                context.returnType != null ? context.returnType : method.getGenericReturnType();
                        CompletableFutureAdapter completableFutureAdapter = findCompletableFutureAdapter(declaredReturnType);
                        boolean asyncReturnType = typeHasRawClass(declaredReturnType, CompletableFuture.class)
                                || typeHasRawClass(declaredReturnType, CompletionStage.class)
                                || completableFutureAdapter != null;
                        Type returnType = declaredReturnType;
                        if (asyncReturnType) {
                            returnType = resolveFirstGenericParameterType(declaredReturnType);
                        }
                        boolean streaming = returnType == TokenStream.class || canAdaptTokenStreamTo(returnType);
                        if (asyncReturnType && streaming) {
                            throw illegalConfiguration(
                                    "The method '%s' cannot return an asynchronous wrapper of a streaming type. "
                                            + "Please use the streaming type directly as the return type.",
                                    method.getName());
                        }

                        PublisherAdapter publisherAdapter = findPublisherAdapter(returnType);
                        boolean reactiveStreaming =
                                typeHasRawClass(returnType, Flow.Publisher.class) || publisherAdapter != null;
                        if (asyncReturnType && reactiveStreaming) {
                            throw illegalConfiguration(
                                    "The method '%s' cannot return an asynchronous wrapper of a reactive streaming type. "
                                            + "Please use the reactive streaming type directly as the return type.",
                                    method.getName());
                        }

                        if ((asyncReturnType || reactiveStreaming) && method.isAnnotationPresent(Moderate.class)) {
                            throw illegalConfiguration(
                                    "The method '%s' cannot be annotated with @Moderate when it returns an asynchronous "
                                            + "(CompletableFuture/CompletionStage) or reactive (Flow.Publisher) type. "
                                            + "Moderation is supported only for synchronous return types.",
                                    method.getName());
                        }

                        // TODO should it be called when returnType==String?
                        boolean supportsJsonSchema = supportsJsonSchema();
                        Optional<JsonSchema> jsonSchema = Optional.empty();
                        boolean returnsImage = isImage(returnType);

                        if (supportsJsonSchema && !streaming && !reactiveStreaming && !returnsImage) {
                            jsonSchema = serviceOutputParser.jsonSchema(returnType);
                        }
                        boolean appendOutputFormat = (!supportsJsonSchema || jsonSchema.isEmpty())
                                && !streaming
                                && !reactiveStreaming
                                && !returnsImage;

                        ResponseFormat responseFormat = null;
                        if (supportsJsonSchema && jsonSchema.isPresent()) {
                            responseFormat = ResponseFormat.builder()
                                    .type(JSON)
                                    .jsonSchema(jsonSchema.get())
                                    .build();
                        }

                        boolean isReturnTypeResult = typeHasRawClass(returnType, Result.class);

                        if (asyncReturnType) {
                            final InvocationContext baseInvocationContext = invocationContext;
                            final Optional<SystemMessage> asyncSystemMessage = systemMessage;
                            final ResponseFormat asyncResponseFormat = responseFormat;
                            final Type asyncReturnType2 = returnType;
                            final boolean asyncAppendOutputFormat = appendOutputFormat;
                            CompletableFuture<Object> result = new CompletableFuture<>();
                            // RAG augmentation runs off the caller thread (memory read + retrieval); the augmented
                            // user message, input guardrails, memory assembly, model call and tool loop then all run
                            // composed. Cancelling the returned future cancels the in-flight augmentation and input
                            // guardrails (best-effort); the model call and the output guardrails are cancelled via
                            // propagateCancellation on the inner loop future.
                            CompletableFuture<AugmentationResult> augmentation = augmentAsyncIfNeeded(
                                    chatMemory, asyncSystemMessage, originalUserMessage, baseInvocationContext);
                            propagateCancellation(result, augmentation);
                            augmentation.whenComplete((augmentationResult, augmentationError) -> {
                                if (augmentationError != null) {
                                    result.completeExceptionally(unwrapCompletionException(augmentationError));
                                    return;
                                }
                                try {
                                    UserMessage augmentedUserMessage = addContentsToUserMessage(
                                            method,
                                            args,
                                            augmentationResult != null
                                                    ? (UserMessage) augmentationResult.chatMessage()
                                                    : originalUserMessage);
                                    GuardrailRequestParams commonGuardrailParam = GuardrailRequestParams.builder()
                                            .chatMemory(chatMemory)
                                            .augmentationResult(augmentationResult)
                                            .userMessageTemplate(userMessageTemplate)
                                            .invocationContext(baseInvocationContext)
                                            .aiServiceListenerRegistrar(context.eventListenerRegistrar)
                                            .variables(variables)
                                            .build();

                                    CompletableFuture<UserMessage> inputGuardrails = invokeInputGuardrailsAsync(
                                            context.guardrailService(), method, augmentedUserMessage, commonGuardrailParam);
                                    propagateCancellation(result, inputGuardrails);
                                    inputGuardrails
                                            .thenApply(guardedUserMessage -> prepareGuardedInput(
                                                    guardedUserMessage,
                                                    baseInvocationContext,
                                                    asyncReturnType2,
                                                    asyncAppendOutputFormat))
                                            .whenComplete((guardedInput, guardrailError) -> {
                                                if (guardrailError != null) {
                                                    result.completeExceptionally(unwrapCompletionException(guardrailError));
                                                    return;
                                                }
                                                assembleMessagesAsync(
                                                                chatMemory,
                                                                asyncSystemMessage,
                                                                guardedInput.userMessage(),
                                                                originalUserMessage)
                                                        .whenComplete((assembledMessages, assemblyError) -> {
                                                            if (assemblyError != null) {
                                                                result.completeExceptionally(
                                                                        unwrapCompletionException(assemblyError));
                                                                return;
                                                            }
                                                            try {
                                                                dispatchAsync(
                                                                        result,
                                                                        method,
                                                                        args,
                                                                        guardedInput.invocationContext(),
                                                                        memoryId,
                                                                        chatMemory,
                                                                        assembledMessages,
                                                                        guardedInput.userMessage(),
                                                                        asyncResponseFormat,
                                                                        commonGuardrailParam,
                                                                        augmentationResult,
                                                                        asyncReturnType2,
                                                                        returnsImage,
                                                                        isReturnTypeResult);
                                                            } catch (Throwable t) {
                                                                result.completeExceptionally(t);
                                                            }
                                                        });
                                            });
                                } catch (Throwable t) {
                                    result.completeExceptionally(t);
                                }
                            });
                            return completableFutureAdapter != null
                                    ? completableFutureAdapter.fromCompletableFuture(declaredReturnType, result)
                                    : result;
                        }

                        if (reactiveStreaming) {
                            Type elementType = resolveFirstGenericParameterType(returnType);
                            if (elementType != AiServiceStreamingEvent.class && elementType != String.class) {
                                throw illegalConfiguration(
                                        "The method '%s' returns a reactive stream of an unsupported element type '%s'. "
                                                + "Supported element types are %s and String.",
                                        method.getName(), elementType, AiServiceStreamingEvent.class.getName());
                            }

                            final InvocationContext baseInvocationContext = invocationContext;
                            final Optional<SystemMessage> reactiveSystemMessage = systemMessage;
                            final ChatMemory reactiveChatMemory = chatMemory;
                            final Type reactiveReturnType = returnType;
                            final boolean reactiveAppendOutputFormat = appendOutputFormat;

                            // Cold stream: RAG augmentation (memory read + retrieval) is started on subscribe, off the
                            // subscriber thread, and the augmented user message, input guardrails, memory assembly and
                            // the streaming tool loop are composed onto it. The subscriber has no Subscription until the
                            // inner publisher subscribes, so augmentation itself is not cancellable; once streaming
                            // starts, cancelling the Subscription stops the interaction (see AiServiceStreamingEventPublisher).
                            Flow.Publisher<AiServiceStreamingEvent> events = subscriber -> augmentAsyncIfNeeded(
                                            reactiveChatMemory,
                                            reactiveSystemMessage,
                                            originalUserMessage,
                                            baseInvocationContext)
                                    .whenComplete((augmentationResult, augmentationError) -> {
                                        if (augmentationError != null) {
                                            subscriber.onSubscribe(NOOP_SUBSCRIPTION);
                                            subscriber.onError(unwrapCompletionException(augmentationError));
                                            return;
                                        }
                                        final UserMessage reactiveInputUserMessage;
                                        final GuardrailRequestParams commonGuardrailParam;
                                        try {
                                            reactiveInputUserMessage = addContentsToUserMessage(
                                                    method,
                                                    args,
                                                    augmentationResult != null
                                                            ? (UserMessage) augmentationResult.chatMessage()
                                                            : originalUserMessage);
                                            commonGuardrailParam = GuardrailRequestParams.builder()
                                                    .chatMemory(reactiveChatMemory)
                                                    .augmentationResult(augmentationResult)
                                                    .userMessageTemplate(userMessageTemplate)
                                                    .invocationContext(baseInvocationContext)
                                                    .aiServiceListenerRegistrar(context.eventListenerRegistrar)
                                                    .variables(variables)
                                                    .build();
                                        } catch (Throwable t) {
                                            subscriber.onSubscribe(NOOP_SUBSCRIPTION);
                                            subscriber.onError(unwrapCompletionException(t));
                                            return;
                                        }
                                        invokeInputGuardrailsAsync(
                                                        context.guardrailService(),
                                                        method,
                                                        reactiveInputUserMessage,
                                                        commonGuardrailParam)
                                                .thenApply(guardedUserMessage -> prepareGuardedInput(
                                                        guardedUserMessage,
                                                        baseInvocationContext,
                                                        reactiveReturnType,
                                                        reactiveAppendOutputFormat))
                                                .whenComplete((guardedInput, guardrailError) -> {
                                                    if (guardrailError != null) {
                                                        subscriber.onSubscribe(NOOP_SUBSCRIPTION);
                                                        subscriber.onError(unwrapCompletionException(guardrailError));
                                                        return;
                                                    }
                                                    assembleMessagesAsync(
                                                                    reactiveChatMemory,
                                                                    reactiveSystemMessage,
                                                                    guardedInput.userMessage(),
                                                                    originalUserMessage)
                                                            .whenComplete((assembledMessages, assemblyError) -> {
                                                                if (assemblyError != null) {
                                                                    subscriber.onSubscribe(NOOP_SUBSCRIPTION);
                                                                    subscriber.onError(
                                                                            unwrapCompletionException(assemblyError));
                                                                    return;
                                                                }
                                                                AiServiceStreamingEventPublisher publisher;
                                                                try {
                                                                    ToolServiceContext reactiveToolServiceContext =
                                                                            context.toolService.createContext(
                                                                                    guardedInput.invocationContext(),
                                                                                    guardedInput.userMessage(),
                                                                                    assembledMessages);
                                                                    var streamingEventStreamParameters =
                                                                            AiServiceTokenStreamParameters.builder()
                                                                                    .messages(assembledMessages)
                                                                                    .toolServiceContext(
                                                                                            reactiveToolServiceContext)
                                                                                    .toolArgumentsErrorHandler(
                                                                                            context.toolService
                                                                                                    .argumentsErrorHandler())
                                                                                    .toolExecutionErrorHandler(
                                                                                            context.toolService
                                                                                                    .executionErrorHandler())
                                                                                    .toolExecutor(
                                                                                            context.toolService.executor())
                                                                                    .retrievedContents(
                                                                                            augmentationResult != null
                                                                                                    ? augmentationResult
                                                                                                            .contents()
                                                                                                    : null)
                                                                                    .context(context)
                                                                                    .invocationContext(
                                                                                            guardedInput
                                                                                                    .invocationContext())
                                                                                    .commonGuardrailParams(
                                                                                            commonGuardrailParam)
                                                                                    .methodKey(method)
                                                                                    .build();
                                                                    publisher = new AiServiceStreamingEventPublisher(
                                                                            streamingEventStreamParameters,
                                                                            context.streamingBufferSize);
                                                                } catch (Throwable t) {
                                                                    subscriber.onSubscribe(NOOP_SUBSCRIPTION);
                                                                    subscriber.onError(unwrapCompletionException(t));
                                                                    return;
                                                                }
                                                                publisher.subscribe(subscriber);
                                                            });
                                                });
                                    });

                            Flow.Publisher<?> mapped = elementType == AiServiceStreamingEvent.class
                                    ? events
                                    : AiServiceStreamingEventPublisher.toTextPublisher(
                                            events,
                                            context.guardrailService().hasOutputGuardrails(method),
                                            context.streamingBufferSize);

                            return publisherAdapter != null
                                    ? publisherAdapter.fromPublisher(returnType, mapped)
                                    : mapped;
                        }

                        // Synchronous path: RAG augmentation on the calling thread (the async and reactive paths above
                        // augment off-thread and have already returned).
                        UserMessage userMessageForAugmentation = originalUserMessage;
                        AugmentationResult augmentationResult = null;
                        if (context.retrievalAugmentor != null) {
                            List<ChatMessage> chatMemoryMessages = chatMemory != null ? chatMemory.messages() : null;
                            Metadata metadata = Metadata.builder()
                                    .chatMessage(userMessageForAugmentation)
                                    .systemMessage(systemMessage.orElse(null))
                                    .chatMemory(chatMemoryMessages)
                                    .invocationContext(invocationContext)
                                    .build();
                            AugmentationRequest augmentationRequest =
                                    new AugmentationRequest(userMessageForAugmentation, metadata);
                            augmentationResult = context.retrievalAugmentor.augment(augmentationRequest);
                            userMessageForAugmentation = (UserMessage) augmentationResult.chatMessage();
                        }

                        UserMessage userMessage = addContentsToUserMessage(method, args, userMessageForAugmentation);

                        var commonGuardrailParam = GuardrailRequestParams.builder()
                                .chatMemory(chatMemory)
                                .augmentationResult(augmentationResult)
                                .userMessageTemplate(userMessageTemplate)
                                .invocationContext(invocationContext)
                                .aiServiceListenerRegistrar(context.eventListenerRegistrar)
                                .variables(variables)
                                .build();

                        userMessage = invokeInputGuardrails(
                                context.guardrailService(), method, userMessage, commonGuardrailParam);
                        if (appendOutputFormat) {
                            userMessage = appendOutputFormatInstructions(returnType, userMessage);
                        }
                        invocationContext = invocationContext.toBuilder()
                                .userMessage(userMessage)
                                .build();

                        List<ChatMessage> messages =
                                assembleMessages(chatMemory, systemMessage, userMessage, originalUserMessage);

                        CompletableFuture<Moderation> moderationFuture = triggerModerationIfNeeded(method, messages);

                        ToolServiceContext toolServiceContext =
                                context.toolService.createContext(invocationContext, userMessage, messages);

                        if (streaming) {
                            var tokenStreamParameters = AiServiceTokenStreamParameters.builder()
                                    .messages(messages)
                                    .toolServiceContext(toolServiceContext)
                                    .toolArgumentsErrorHandler(context.toolService.argumentsErrorHandler())
                                    .toolExecutionErrorHandler(context.toolService.executionErrorHandler())
                                    .toolExecutor(context.toolService.executor())
                                    .retrievedContents(
                                            augmentationResult != null ? augmentationResult.contents() : null)
                                    .context(context)
                                    .invocationContext(invocationContext)
                                    .commonGuardrailParams(commonGuardrailParam)
                                    .methodKey(method)
                                    .build();

                            TokenStream tokenStream = new AiServiceTokenStream(tokenStreamParameters);
                            if (returnType == TokenStream.class) {
                                return tokenStream;
                            } else {
                                return adapt(tokenStream, returnType);
                            }
                        }

                        ChatRequestParameters parameters =
                                chatRequestParameters(method, args, toolServiceContext, responseFormat);

                        ChatRequest chatRequest = context.chatRequestTransformer.apply(
                                ChatRequest.builder()
                                        .messages(messages)
                                        .parameters(parameters)
                                        .build(),
                                memoryId);

                        ChatExecutor chatExecutor = ChatExecutor.builder(context.chatModel)
                                .chatRequest(chatRequest)
                                .invocationContext(invocationContext)
                                .eventListenerRegistrar(context.eventListenerRegistrar)
                                .build();

                        ChatResponse chatResponse = chatExecutor.execute();

                        context.eventListenerRegistrar.fireEvent(AiServiceResponseReceivedEvent.builder()
                                .invocationContext(invocationContext)
                                .response(chatResponse)
                                .request(chatRequest)
                                .build());

                        verifyModerationIfNeeded(moderationFuture);

                        ToolServiceResult toolServiceResult = context.toolService.executeInferenceAndToolsLoop(
                                context,
                                memoryId,
                                chatResponse,
                                parameters,
                                messages,
                                chatMemory,
                                invocationContext,
                                toolServiceContext,
                                context.chatModel::chat);

                        return processToolServiceResult(
                                method,
                                invocationContext,
                                returnType,
                                returnsImage,
                                isReturnTypeResult,
                                augmentationResult,
                                toolServiceContext,
                                toolServiceResult,
                                chatExecutor,
                                memoryId,
                                parameters,
                                commonGuardrailParam);
                    }

                    private CompletableFuture<Object> invokeAsync(
                            Method method,
                            InvocationContext invocationContext,
                            Object memoryId,
                            ChatMemory chatMemory,
                            List<ChatMessage> messages,
                            ChatRequest chatRequest,
                            ChatExecutor chatExecutor,
                            ChatRequestParameters parameters,
                            CompletableFuture<Moderation> moderationFuture,
                            ToolServiceContext toolServiceContext,
                            GuardrailRequestParams commonGuardrailParam,
                            AugmentationResult augmentationResult,
                            Type returnType,
                            boolean returnsImage,
                            boolean isReturnTypeResult) {

                        context.eventListenerRegistrar.fireEvent(AiServiceRequestIssuedEvent.builder()
                                .invocationContext(invocationContext)
                                .request(chatRequest)
                                .build());

                        CompletableFuture<Object> result = new CompletableFuture<>();

                        CompletableFuture<ChatResponse> firstModelCall = context.chatModel.chatAsync(chatRequest);
                        propagateCancellation(result, firstModelCall);

                        firstModelCall
                                .thenCompose(chatResponse -> {
                                    context.eventListenerRegistrar.fireEvent(AiServiceResponseReceivedEvent.builder()
                                            .invocationContext(invocationContext)
                                            .response(chatResponse)
                                            .request(chatRequest)
                                            .build());

                                    CompletableFuture<Void> moderationVerified = moderationFuture == null
                                            ? CompletableFuture.completedFuture(null)
                                            : moderationFuture.thenAccept(AiServices::verifyModeration);

                                    return moderationVerified.thenCompose(
                                            ignored -> context.toolService.executeInferenceAndToolsLoopAsync(
                                                    context,
                                                    memoryId,
                                                    chatResponse,
                                                    parameters,
                                                    messages,
                                                    chatMemory,
                                                    invocationContext,
                                                    toolServiceContext,
                                                    result));
                                })
                                .thenCompose(toolServiceResult -> processToolServiceResultAsync(
                                        method,
                                        invocationContext,
                                        returnType,
                                        returnsImage,
                                        isReturnTypeResult,
                                        augmentationResult,
                                        toolServiceContext,
                                        toolServiceResult,
                                        chatExecutor,
                                        memoryId,
                                        parameters,
                                        commonGuardrailParam))
                                .whenComplete((value, error) -> {
                                    if (error != null) {
                                        Throwable cause = unwrapCompletionException(error);
                                        if (!result.isCancelled() && !(cause instanceof CancellationException)) {
                                            context.eventListenerRegistrar.fireEvent(AiServiceErrorEvent.builder()
                                                    .invocationContext(invocationContext)
                                                    .error(cause instanceof Exception exception
                                                            ? exception
                                                            : new RuntimeException(cause))
                                                    .build());
                                        }
                                        result.completeExceptionally(cause);
                                    } else {
                                        result.complete(value);
                                    }
                                });

                        return result;
                    }

                    /**
                     * Builds the request artifacts from the (already assembled) messages and runs the asynchronous
                     * pipeline, piping the outcome into {@code result}. Invoked off the caller thread once
                     * {@link #assembleMessagesAsync} has resolved, so no chat-memory I/O blocks the caller.
                     */
                    private void dispatchAsync(
                            CompletableFuture<Object> result,
                            Method method,
                            Object[] args,
                            InvocationContext invocationContext,
                            Object memoryId,
                            ChatMemory chatMemory,
                            List<ChatMessage> messages,
                            UserMessage userMessage,
                            ResponseFormat responseFormat,
                            GuardrailRequestParams commonGuardrailParam,
                            AugmentationResult augmentationResult,
                            Type returnType,
                            boolean returnsImage,
                            boolean isReturnTypeResult) {

                        if (result.isCancelled()) {
                            return;
                        }

                        ToolServiceContext toolServiceContext =
                                context.toolService.createContext(invocationContext, userMessage, messages);
                        ChatRequestParameters parameters =
                                chatRequestParameters(method, args, toolServiceContext, responseFormat);
                        ChatRequest chatRequest = context.chatRequestTransformer.apply(
                                ChatRequest.builder()
                                        .messages(messages)
                                        .parameters(parameters)
                                        .build(),
                                memoryId);
                        ChatExecutor chatExecutor = ChatExecutor.builder(context.chatModel)
                                .chatRequest(chatRequest)
                                .invocationContext(invocationContext)
                                .eventListenerRegistrar(context.eventListenerRegistrar)
                                .build();

                        CompletableFuture<Object> inner = invokeAsync(
                                method,
                                invocationContext,
                                memoryId,
                                chatMemory,
                                messages,
                                chatRequest,
                                chatExecutor,
                                parameters,
                                null,
                                toolServiceContext,
                                commonGuardrailParam,
                                augmentationResult,
                                returnType,
                                returnsImage,
                                isReturnTypeResult);
                        propagateCancellation(result, inner);
                        inner.whenComplete((value, error) -> {
                            if (error != null) {
                                result.completeExceptionally(unwrapCompletionException(error));
                            } else {
                                result.complete(value);
                            }
                        });
                    }

                    /**
                     * Assembles the messages to send (system message, prior memory, user message), reading and
                     * writing chat memory synchronously. Used by the synchronous, {@code TokenStream} and reactive
                     * modes.
                     */
                    private List<ChatMessage> assembleMessages(
                            ChatMemory chatMemory,
                            Optional<SystemMessage> systemMessage,
                            UserMessage userMessage,
                            UserMessage originalUserMessage) {
                        List<ChatMessage> messages = new ArrayList<>();
                        if (context.hasChatMemory()) {
                            systemMessage.ifPresent(chatMemory::add);
                            messages.addAll(chatMemory.messages());
                            if (context.storeRetrievedContentInChatMemory) {
                                chatMemory.add(userMessage);
                            } else {
                                chatMemory.add(originalUserMessage);
                            }
                            messages.add(userMessage);
                        } else {
                            systemMessage.ifPresent(messages::add);
                            messages.add(userMessage);
                        }
                        return messages;
                    }

                    /**
                     * Runs RAG augmentation off the calling thread for the asynchronous and reactive return-type
                     * modes: reads the chat memory via the asynchronous {@code messagesAsync} and delegates to
                     * {@code RetrievalAugmentor.augmentAsync}. Completes with {@code null} when no
                     * {@code RetrievalAugmentor} is configured (the caller then uses the original user message).
                     */
                    private CompletableFuture<AugmentationResult> augmentAsyncIfNeeded(
                            ChatMemory chatMemory,
                            Optional<SystemMessage> systemMessage,
                            UserMessage originalUserMessage,
                            InvocationContext invocationContext) {
                        RetrievalAugmentor augmentor = context.retrievalAugmentor;
                        if (augmentor == null) {
                            return CompletableFuture.completedFuture(null);
                        }
                        // Try the native async path off the caller thread. A blocking augmentor (its augmentAsync
                        // reports UnsupportedOperationException) or a blocking chat memory (messagesAsync) surfaces at
                        // runtime; we then either offload or fail loudly - no reflection to detect it in advance. Any
                        // synchronous throw becomes a failed future, never a thrown exception (which on the reactive
                        // path would escape subscribe() and violate the Reactive Streams contract).
                        CompletableFuture<AugmentationResult> async;
                        try {
                            CompletableFuture<List<ChatMessage>> chatMemoryMessages = chatMemory != null
                                    ? chatMemory.messagesAsync()
                                    : CompletableFuture.completedFuture(null);
                            async = chatMemoryMessages.thenCompose(memoryMessages -> augmentor.augmentAsync(
                                    augmentationRequest(
                                            originalUserMessage, systemMessage, memoryMessages, invocationContext)));
                        } catch (Throwable t) {
                            async = CompletableFuture.failedFuture(t);
                        }
                        return async.exceptionallyCompose(error -> {
                            Throwable cause = unwrapCompletionException(error);
                            if (cause instanceof AsyncNotSupportedException) {
                                // A blocking RetrievalAugmentor or ChatMemory on the async/reactive path fails loudly -
                                // the component that does the blocking work decides whether to offload, not the AI
                                // Service. Implement the async method (augmentAsync / messagesAsync), or configure an
                                // async-capable component (e.g. DefaultRetrievalAugmentor.builder().offloadBlocking(true)).
                                return CompletableFuture.failedFuture(new UnsupportedFeatureException(cause.getMessage()
                                        + " The asynchronous/reactive AI Service requires this component to implement its"
                                        + " async method, or to be an async-capable component (for a blocking content"
                                        + " retriever, configure DefaultRetrievalAugmentor.builder().offloadBlocking(true)"
                                        + " and pass it via retrievalAugmentor(...))."));
                            }
                            return CompletableFuture.failedFuture(error);
                        });
                    }

                    private AugmentationRequest augmentationRequest(
                            UserMessage originalUserMessage,
                            Optional<SystemMessage> systemMessage,
                            List<ChatMessage> memoryMessages,
                            InvocationContext invocationContext) {
                        Metadata metadata = Metadata.builder()
                                .chatMessage(originalUserMessage)
                                .systemMessage(systemMessage.orElse(null))
                                .chatMemory(memoryMessages)
                                .invocationContext(invocationContext)
                                .build();
                        return new AugmentationRequest(originalUserMessage, metadata);
                    }

                    /**
                     * Non-blocking counterpart of {@link #assembleMessages}: reads and writes chat memory via the
                     * asynchronous {@link ChatMemory} methods so the caller thread is not blocked on (potentially
                     * I/O-backed) memory. The writes are chained (never concurrent) so the underlying
                     * read-modify-write stays ordered.
                     */
                    private CompletionStage<List<ChatMessage>> assembleMessagesAsync(
                            ChatMemory chatMemory,
                            Optional<SystemMessage> systemMessage,
                            UserMessage userMessage,
                            UserMessage originalUserMessage) {
                        if (!context.hasChatMemory()) {
                            List<ChatMessage> messages = new ArrayList<>();
                            systemMessage.ifPresent(messages::add);
                            messages.add(userMessage);
                            return CompletableFuture.completedFuture(messages);
                        }

                        try {
                            CompletionStage<Void> addSystem = systemMessage.isPresent()
                                    ? chatMemory.addAsync(List.of(systemMessage.get()))
                                    : CompletableFuture.completedFuture(null);
                            ChatMessage userMessageToStore =
                                    context.storeRetrievedContentInChatMemory ? userMessage : originalUserMessage;
                            return addSystem.thenCompose(ignored -> chatMemory.messagesAsync())
                                    .thenCompose(history -> {
                                        List<ChatMessage> messages = new ArrayList<>(history);
                                        return chatMemory.addAsync(List.of(userMessageToStore)).thenApply(ignored2 -> {
                                            messages.add(userMessage);
                                            return messages;
                                        });
                                    });
                        } catch (Throwable t) {
                            return CompletableFuture.failedFuture(t);
                        }
                    }

                    private final Object fallThroughToOutputProcessing = new Object();

                    private Object processToolServiceResult(
                            Method method,
                            InvocationContext invocationContext,
                            Type returnType,
                            boolean returnsImage,
                            boolean isReturnTypeResult,
                            AugmentationResult augmentationResult,
                            ToolServiceContext toolServiceContext,
                            ToolServiceResult toolServiceResult,
                            ChatExecutor chatExecutor,
                            Object memoryId,
                            ChatRequestParameters parameters,
                            GuardrailRequestParams commonGuardrailParam) {

                        if (toolServiceResult.immediateToolReturn()) {
                            Object immediate = immediateToolReturnResult(
                                    method,
                                    invocationContext,
                                    returnType,
                                    isReturnTypeResult,
                                    augmentationResult,
                                    toolServiceContext,
                                    toolServiceResult);
                            if (immediate != fallThroughToOutputProcessing) {
                                return immediate;
                            }
                        }

                        ChatResponse aggregateResponse = toolServiceResult.aggregateResponse();

                        ChatExecutor toolAwareRepromptExecutor = ToolAwareRepromptExecutor.wrap(
                                chatExecutor,
                                context,
                                memoryId,
                                parameters,
                                invocationContext,
                                toolServiceContext,
                                context.chatModel::chat);

                        var response = invokeOutputGuardrails(
                                context.guardrailService(),
                                method,
                                aggregateResponse,
                                toolAwareRepromptExecutor,
                                commonGuardrailParam);

                        return finishToolServiceResult(
                                response,
                                invocationContext,
                                returnType,
                                returnsImage,
                                isReturnTypeResult,
                                augmentationResult,
                                toolServiceResult);
                    }

                    /**
                     * Non-blocking counterpart of {@link #processToolServiceResult}: the output guardrails (and any
                     * reprompt round-trips to the model) run via {@link GuardrailService#executeGuardrailsAsync} so the
                     * model-delivery thread is never blocked; the (CPU-bound) output parsing then runs in the
                     * completion stage.
                     */
                    private CompletableFuture<Object> processToolServiceResultAsync(
                            Method method,
                            InvocationContext invocationContext,
                            Type returnType,
                            boolean returnsImage,
                            boolean isReturnTypeResult,
                            AugmentationResult augmentationResult,
                            ToolServiceContext toolServiceContext,
                            ToolServiceResult toolServiceResult,
                            ChatExecutor chatExecutor,
                            Object memoryId,
                            ChatRequestParameters parameters,
                            GuardrailRequestParams commonGuardrailParam) {

                        if (toolServiceResult.immediateToolReturn()) {
                            Object immediate = immediateToolReturnResult(
                                    method,
                                    invocationContext,
                                    returnType,
                                    isReturnTypeResult,
                                    augmentationResult,
                                    toolServiceContext,
                                    toolServiceResult);
                            if (immediate != fallThroughToOutputProcessing) {
                                return CompletableFuture.completedFuture(immediate);
                            }
                        }

                        ChatResponse aggregateResponse = toolServiceResult.aggregateResponse();

                        ChatExecutor toolAwareRepromptExecutor = ToolAwareRepromptExecutor.wrapAsync(
                                chatExecutor,
                                context,
                                memoryId,
                                parameters,
                                invocationContext,
                                toolServiceContext,
                                context.chatModel::chatAsync);

                        return DefaultAiServices.this
                                .<Object>invokeOutputGuardrailsAsync(
                                        context.guardrailService(),
                                        method,
                                        aggregateResponse,
                                        toolAwareRepromptExecutor,
                                        commonGuardrailParam)
                                .thenApply(response -> finishToolServiceResult(
                                        response,
                                        invocationContext,
                                        returnType,
                                        returnsImage,
                                        isReturnTypeResult,
                                        augmentationResult,
                                        toolServiceResult));
                    }

                    private Object immediateToolReturnResult(
                            Method method,
                            InvocationContext invocationContext,
                            Type returnType,
                            boolean isReturnTypeResult,
                            AugmentationResult augmentationResult,
                            ToolServiceContext toolServiceContext,
                            ToolServiceResult toolServiceResult) {

                        if (isReturnTypeResult) {
                            var result = Result.builder()
                                    .content(null)
                                    .tokenUsage(toolServiceResult.aggregateTokenUsage())
                                    .sources(augmentationResult == null ? null : augmentationResult.contents())
                                    .finishReason(TOOL_EXECUTION)
                                    .toolExecutions(toolServiceResult.toolExecutions())
                                    .intermediateResponses(toolServiceResult.intermediateResponses())
                                    .finalResponse(toolServiceResult.finalResponse())
                                    .build();

                            return fireEventAndReturn(invocationContext, result);
                        }
                        if (returnType == void.class || returnType == Void.class) {
                            return fireEventAndReturn(invocationContext, null);
                        }
                        Set<ReturnBehavior> returnBehaviors = toolServiceResult.toolExecutions().stream()
                                .map(execution -> toolServiceContext.returnBehavior(
                                        execution.request().name()))
                                .collect(Collectors.toSet());
                        if (returnBehaviors.stream()
                                .allMatch(returnBehavior -> returnBehavior == ReturnBehavior.IMMEDIATE
                                        || returnBehavior == ReturnBehavior.IMMEDIATE_IF_LAST)) {
                            int numNullResults = 0;
                            ToolExecution lastNonNull = null;
                            for (ToolExecution execution : toolServiceResult.toolExecutions()) {
                                if (execution.resultObject() == null) {
                                    numNullResults++;
                                } else {
                                    lastNonNull = execution;
                                }
                            }
                            if (numNullResults
                                    == toolServiceResult.toolExecutions().size()) {
                                return fireEventAndReturn(invocationContext, null);
                            } else if (numNullResults + 1
                                            == toolServiceResult
                                                    .toolExecutions()
                                                    .size()
                                    && resolvesToType(lastNonNull.resultObject(), returnType)) {
                                return fireEventAndReturn(invocationContext, lastNonNull.resultObject());
                            }
                            throw illegalConfiguration(
                                    "AI Service method '%s' call cannot resolve return type from tool executions with ReturnBehavior.%s/%s. Use %s as your return type.",
                                    method.getName(), IMMEDIATE, IMMEDIATE_IF_LAST, Result.class.getName());
                        }

                        return fallThroughToOutputProcessing;
                    }

                    private Object finishToolServiceResult(
                            Object response,
                            InvocationContext invocationContext,
                            Type returnType,
                            boolean returnsImage,
                            boolean isReturnTypeResult,
                            AugmentationResult augmentationResult,
                            ToolServiceResult toolServiceResult) {

                        if (response != null) {
                            if (returnsImage && response instanceof ChatResponse cResponse) {
                                return fireEventAndReturn(invocationContext, parseImages(cResponse, returnType));
                            }

                            if (typeHasRawClass(returnType, response.getClass())) {
                                return fireEventAndReturn(invocationContext, response);
                            }
                        }

                        var parsedResponse = serviceOutputParser.parse((ChatResponse) response, returnType);
                        var actualResponse = (isReturnTypeResult)
                                ? Result.builder()
                                        .content(parsedResponse)
                                        .tokenUsage(toolServiceResult.aggregateTokenUsage())
                                        .sources(augmentationResult == null ? null : augmentationResult.contents())
                                        .finishReason(toolServiceResult
                                                .finalResponse()
                                                .finishReason())
                                        .toolExecutions(toolServiceResult.toolExecutions())
                                        .intermediateResponses(toolServiceResult.intermediateResponses())
                                        .finalResponse(toolServiceResult.finalResponse())
                                        .build()
                                : parsedResponse;

                        return fireEventAndReturn(invocationContext, actualResponse);
                    }

                    private Object fireEventAndReturn(InvocationContext invocationContext, Object result) {
                        context.eventListenerRegistrar.fireEvent(AiServiceCompletedEvent.builder()
                                .invocationContext(invocationContext)
                                .result(result)
                                .build());
                        return result;
                    }

                    private static boolean isImage(Type returnType) {
                        Class<?> rawReturnType = getRawClass(returnType);
                        if (isImageType(rawReturnType)) {
                            return true;
                        }
                        if (Collection.class.isAssignableFrom(rawReturnType)) {
                            Class<?> genericParam = resolveFirstGenericParameterClass(returnType);
                            return genericParam != null && isImageType(genericParam);
                        }
                        return false;
                    }

                    private static Object parseImages(ChatResponse response, Type returnType) {
                        List<Image> images = response.aiMessage().images();
                        Class<?> rawReturnType = getRawClass(returnType);
                        if (isImage(rawReturnType)) {
                            if (rawReturnType == ImageContent.class) {
                                List<ImageContent> imageContents = toImageContents(images);
                                return imageContents.isEmpty() ? null : imageContents.get(0);
                            }
                            if (rawReturnType == Image.class) {
                                return images.isEmpty() ? null : images.get(0);
                            }
                        }
                        if (Collection.class.isAssignableFrom(rawReturnType)) {
                            Class<?> genericParam = resolveFirstGenericParameterClass(returnType);
                            if (genericParam == ImageContent.class) {
                                return toImageContents(images);
                            }
                            if (genericParam == Image.class) {
                                return images;
                            }
                        }
                        throw new UnsupportedOperationException("Unsupported return type " + rawReturnType);
                    }

                    private static List<ImageContent> toImageContents(List<Image> images) {
                        return images.stream().map(ImageContent::from).toList();
                    }

                    private boolean canAdaptTokenStreamTo(Type returnType) {
                        for (TokenStreamAdapter tokenStreamAdapter : tokenStreamAdapters) {
                            if (tokenStreamAdapter.canAdaptTokenStreamTo(returnType)) {
                                return true;
                            }
                        }
                        return false;
                    }

                    private CompletableFutureAdapter findCompletableFutureAdapter(Type returnType) {
                        for (CompletableFutureAdapter adapter : completableFutureAdapters) {
                            if (adapter.canAdapt(returnType)) {
                                return adapter;
                            }
                        }
                        return null;
                    }

                    private PublisherAdapter findPublisherAdapter(Type returnType) {
                        for (PublisherAdapter adapter : publisherAdapters) {
                            if (adapter.canAdapt(returnType)) {
                                return adapter;
                            }
                        }
                        return null;
                    }

                    private Object adapt(TokenStream tokenStream, Type returnType) {
                        for (TokenStreamAdapter tokenStreamAdapter : tokenStreamAdapters) {
                            if (tokenStreamAdapter.canAdaptTokenStreamTo(returnType)) {
                                return tokenStreamAdapter.adapt(tokenStream);
                            }
                        }
                        throw new IllegalStateException("Can't find suitable TokenStreamAdapter");
                    }

                    private boolean supportsJsonSchema() {
                        return context.chatModel != null
                                && context.chatModel.supportedCapabilities().contains(RESPONSE_FORMAT_JSON_SCHEMA);
                    }

                    /**
                     * Applies the user-message-dependent prelude after input guardrails have produced the
                     * (possibly rewritten) user message: optionally appends the output-format instructions and
                     * rebuilds the {@link InvocationContext} to carry the final user message.
                     */
                    private GuardedInput prepareGuardedInput(
                            UserMessage guardedUserMessage,
                            InvocationContext baseInvocationContext,
                            Type returnType,
                            boolean appendOutputFormat) {
                        UserMessage userMessage = appendOutputFormat
                                ? appendOutputFormatInstructions(returnType, guardedUserMessage)
                                : guardedUserMessage;
                        InvocationContext invocationContext = baseInvocationContext.toBuilder()
                                .userMessage(userMessage)
                                .build();
                        return new GuardedInput(userMessage, invocationContext);
                    }

                    private UserMessage appendOutputFormatInstructions(Type returnType, UserMessage userMessage) {
                        String outputFormatInstructions = serviceOutputParser.outputFormatInstructions(returnType);
                        if (isNullOrEmpty(outputFormatInstructions)) {
                            return userMessage;
                        }

                        List<Content> contents = new ArrayList<>(userMessage.contents());

                        boolean appended = false;
                        for (int i = contents.size() - 1; i >= 0; i--) {
                            if (contents.get(i) instanceof TextContent lastTextContent) {
                                String newText = lastTextContent.text() + outputFormatInstructions;
                                contents.set(i, TextContent.from(newText));
                                appended = true;
                                break;
                            }
                        }

                        if (!appended) {
                            contents.add(TextContent.from(outputFormatInstructions));
                        }

                        return userMessage.toBuilder().contents(contents).build();
                    }

                    private CompletableFuture<Moderation> triggerModerationIfNeeded(
                            Method method, List<ChatMessage> messages) {
                        if (method.isAnnotationPresent(Moderate.class)) {
                            Executor executor = DefaultExecutorProvider.getDefaultExecutor();
                            return CompletableFuture.supplyAsync(
                                    () -> {
                                        List<ChatMessage> messagesToModerate = removeToolMessages(messages);
                                        return context.moderationModel
                                                .moderate(messagesToModerate)
                                                .content();
                                    },
                                    executor);
                        }
                        return null;
                    }
                });

        return (T) proxyInstance;
    }

    private static boolean resolvesToType(Object o, Type returnType) {
        return o != null && returnType instanceof Class && ((Class) returnType).isAssignableFrom(o.getClass());
    }

    private UserMessage invokeInputGuardrails(
            GuardrailService guardrailService,
            Method method,
            UserMessage userMessage,
            GuardrailRequestParams commonGuardrailParams) {

        // NOTE: This check is cached, so it really only needs to be computed the first time for each method
        if (guardrailService.hasInputGuardrails(method)) {
            var inputGuardrailRequest = InputGuardrailRequest.builder()
                    .userMessage(userMessage)
                    .commonParams(commonGuardrailParams)
                    .build();
            return guardrailService.executeGuardrails(method, inputGuardrailRequest);
        }

        return userMessage;
    }

    private CompletableFuture<UserMessage> invokeInputGuardrailsAsync(
            GuardrailService guardrailService,
            Method method,
            UserMessage userMessage,
            GuardrailRequestParams commonGuardrailParams) {

        if (guardrailService.hasInputGuardrails(method)) {
            var inputGuardrailRequest = InputGuardrailRequest.builder()
                    .userMessage(userMessage)
                    .commonParams(commonGuardrailParams)
                    .build();
            return guardrailService.executeGuardrailsAsync(method, inputGuardrailRequest);
        }

        return CompletableFuture.completedFuture(userMessage);
    }

    /**
     * The (possibly rewritten) user message produced by the input guardrails together with the
     * {@link InvocationContext} carrying it, as prepared for dispatch.
     */
    private record GuardedInput(UserMessage userMessage, InvocationContext invocationContext) {}

    private <T> T invokeOutputGuardrails(
            GuardrailService guardrailService,
            Method method,
            ChatResponse responseFromLLM,
            ChatExecutor chatExecutor,
            GuardrailRequestParams commonGuardrailParams) {

        if (guardrailService.hasOutputGuardrails(method)) {
            var outputGuardrailRequest = OutputGuardrailRequest.builder()
                    .responseFromLLM(responseFromLLM)
                    .chatExecutor(chatExecutor)
                    .requestParams(commonGuardrailParams)
                    .build();
            return guardrailService.executeGuardrails(method, outputGuardrailRequest);
        }

        return (T) responseFromLLM;
    }

    private <T> CompletableFuture<T> invokeOutputGuardrailsAsync(
            GuardrailService guardrailService,
            Method method,
            ChatResponse responseFromLLM,
            ChatExecutor chatExecutor,
            GuardrailRequestParams commonGuardrailParams) {

        if (guardrailService.hasOutputGuardrails(method)) {
            var outputGuardrailRequest = OutputGuardrailRequest.builder()
                    .responseFromLLM(responseFromLLM)
                    .chatExecutor(chatExecutor)
                    .requestParams(commonGuardrailParams)
                    .build();
            return guardrailService.executeGuardrailsAsync(method, outputGuardrailRequest);
        }

        return CompletableFuture.completedFuture((T) responseFromLLM);
    }

    private Optional<SystemMessage> prepareSystemMessage(
            InvocationContext invocationContext, Method method, Object[] args) {
        return findSystemMessageTemplate(invocationContext, method)
                .map(systemMessageTemplate -> PromptTemplate.from(systemMessageTemplate)
                        .apply(InternalReflectionVariableResolver.findTemplateVariables(
                                systemMessageTemplate, method, args))
                        .toSystemMessage());
    }

    private Optional<String> findSystemMessageTemplate(InvocationContext invocationContext, Method method) {
        dev.langchain4j.service.SystemMessage annotation =
                method.getAnnotation(dev.langchain4j.service.SystemMessage.class);
        if (annotation != null) {
            return Optional.of(getTemplate(
                    method, "System", annotation.fromResource(), annotation.value(), annotation.delimiter()));
        }
        if (context.systemMessageProviderWithContext != null) {
            return Optional.of(context.systemMessageProviderWithContext.apply(invocationContext));
        } else {
            return context.systemMessageProvider.apply(invocationContext.chatMemoryId());
        }
    }

    private static UserMessage prepareUserMessage(
            Method method, Object[] args, String userMessageTemplate, Map<String, Object> variables) {

        Optional<String> maybeUserName = findUserName(method.getParameters(), args);

        if (userMessageTemplate.isEmpty()) {
            List<Content> contents = new ArrayList<>();

            for (Object arg : args) {
                if (arg instanceof Content content) {
                    contents.add(content);
                } else if (isListOfContents(arg)) {
                    contents.addAll((List<Content>) arg);
                }
            }

            if (!contents.isEmpty()) {
                return maybeUserName
                        .map(userName -> UserMessage.from(userName, contents))
                        .orElseGet(() -> UserMessage.from(contents));
            }

            throw illegalConfiguration(
                    "Error: The method '%s' does not have a user message defined.", method.getName());
        }

        Prompt prompt = PromptTemplate.from(userMessageTemplate).apply(variables);

        return maybeUserName
                .map(userName -> UserMessage.from(userName, prompt.text()))
                .orElseGet(prompt::toUserMessage);
    }

    private String getUserMessageTemplate(Object memoryId, Method method, Object[] args) {

        Optional<String> templateFromMethodAnnotation = findUserMessageTemplateFromMethodAnnotation(method);
        Optional<String> templateFromParameterAnnotation =
                findUserMessageTemplateFromAnnotatedParameter(method.getParameters(), args);

        if (templateFromMethodAnnotation.isPresent() && templateFromParameterAnnotation.isPresent()) {
            throw illegalConfiguration(
                    "Error: The method '%s' has multiple @UserMessage annotations. Please use only one.",
                    method.getName());
        }

        if (templateFromMethodAnnotation.isPresent()) {
            return templateFromMethodAnnotation.get();
        }
        if (templateFromParameterAnnotation.isPresent()) {
            return templateFromParameterAnnotation.get();
        }

        Optional<String> templateFromTheOnlyArgument =
                findUserMessageTemplateFromTheOnlyArgument(method.getParameters(), args);
        if (templateFromTheOnlyArgument.isPresent()) {
            return templateFromTheOnlyArgument.get();
        }

        if (hasContentArgument(method, args)) {
            return "";
        }

        return context.userMessageProvider
                .apply(memoryId)
                .orElseThrow(() -> illegalConfiguration(
                        "Error: The method '%s' does not have a user message defined.", method.getName()));
    }

    private static boolean hasContentArgument(Method method, Object[] args) {
        Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            if (parameters[i].isAnnotationPresent(dev.langchain4j.service.UserMessage.class)) {
                if (args[i] instanceof Content || isListOfContents(args[i])) {
                    return true;
                }
            }
        }

        if (parameters.length == 1 && !hasAnyValidAnnotation(parameters[0])) {
            return args[0] instanceof Content || isListOfContents(args[0]);
        }
        return false;
    }

    private static Optional<String> findUserMessageTemplateFromMethodAnnotation(Method method) {
        return Optional.ofNullable(method.getAnnotation(dev.langchain4j.service.UserMessage.class))
                .map(a -> getTemplate(method, "User", a.fromResource(), a.value(), a.delimiter()));
    }

    private static Optional<String> findUserMessageTemplateFromAnnotatedParameter(
            Parameter[] parameters, Object[] args) {
        for (int i = 0; i < parameters.length; i++) {
            if (parameters[i].isAnnotationPresent(dev.langchain4j.service.UserMessage.class)
                    && !(args[i] instanceof Content)
                    && !isListOfContents(args[i])) {
                return Optional.of(InternalReflectionVariableResolver.asString(args[i]));
            }
        }
        return Optional.empty();
    }

    private static boolean hasAnyValidAnnotation(Parameter parameter) {
        for (Class<? extends Annotation> a : VALID_PARAM_ANNOTATIONS) {
            if (parameter.getAnnotation(a) != null) {
                return true;
            }
        }

        return false;
    }

    private static Optional<String> findUserMessageTemplateFromTheOnlyArgument(Parameter[] parameters, Object[] args) {
        if (parameters != null && parameters.length == 1 && !hasAnyValidAnnotation(parameters[0])) {
            if (args[0] instanceof Content || isListOfContents(args[0]) || isMapOfContents(args[0])) {
                return Optional.empty();
            }
            return Optional.of(InternalReflectionVariableResolver.asString(args[0]));
        }
        return Optional.empty();
    }

    private static Optional<String> findUserName(Parameter[] parameters, Object[] args) {
        for (int i = 0; i < parameters.length; i++) {
            if (parameters[i].isAnnotationPresent(UserName.class)) {
                return Optional.of(args[i].toString());
            }
        }
        return Optional.empty();
    }

    private static UserMessage addContentsToUserMessage(Method method, Object[] args, UserMessage userMessage) {
        boolean hasTextContent = false;
        List<Content> contents = new ArrayList<>();

        if (args != null && args.length == 1 && args[0] instanceof Map<?, ?> map && !map.isEmpty()) {
            for (Object value : map.values()) {
                if (value instanceof Content content) {
                    hasTextContent |= value instanceof TextContent;
                    contents.add(content);
                } else if (isListOfContents(value)) {
                    hasTextContent |= ((List<Content>) value).stream().anyMatch(TextContent.class::isInstance);
                    contents.addAll((List<Content>) value);
                }
            }

            if (!contents.isEmpty()) {
                prependTextContentsToUserMessage(userMessage, contents);
                return userMessage.toBuilder().contents(contents).build();
            }
        }

        Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            if (parameters[i].isAnnotationPresent(dev.langchain4j.service.UserMessage.class)) {
                if (args[i] instanceof Content content) {
                    contents.add(content);
                } else if (isListOfContents(args[i])) {
                    hasTextContent |= ((List<Content>) args[i]).stream().anyMatch(TextContent.class::isInstance);
                    contents.addAll((List<Content>) args[i]);
                } else {
                    if (hasTextContent) {
                        throw illegalConfiguration(
                                "Error: The method '%s' has multiple @UserMessage annotations. Please use only one.",
                                method.getName());
                    }
                    contents.addAll(userMessage.contents());
                    hasTextContent = true;
                }
            }
        }

        if (contents.isEmpty() && parameters.length == 1 && !hasAnyValidAnnotation(parameters[0])) {
            if (args[0] instanceof Content) {
                hasTextContent |= args[0] instanceof TextContent;
                contents.add((Content) args[0]);
            } else if (isListOfContents(args[0])) {
                hasTextContent |= ((List<Content>) args[0]).stream().anyMatch(TextContent.class::isInstance);
                contents.addAll((List<Content>) args[0]);
            }
        }

        if (!hasTextContent) {
            prependTextContentsToUserMessage(userMessage, contents);
        }

        return userMessage.contents().size() == contents.size()
                ? userMessage
                : userMessage.toBuilder().contents(contents).build();
    }

    private static void prependTextContentsToUserMessage(UserMessage userMessage, List<Content> contents) {
        List<Content> originalContent = userMessage.contents();
        for (int i = originalContent.size() - 1; i >= 0; i--) {
            if (originalContent.get(i) instanceof TextContent textContent) {
                contents.add(0, textContent);
            }
        }
    }

    private static boolean isMapOfContents(Object o) {
        return o instanceof Map<?, ?> map && map.values().stream().allMatch(Content.class::isInstance);
    }

    private static boolean isListOfContents(Object o) {
        return o instanceof List<?> list && list.stream().allMatch(Content.class::isInstance);
    }

    private static String getTemplate(Method method, String type, String resource, String[] value, String delimiter) {
        String messageTemplate;
        if (!resource.trim().isEmpty()) {
            messageTemplate = getResourceText(method.getDeclaringClass(), resource);
            if (messageTemplate == null) {
                throw illegalConfiguration("@%sMessage's resource '%s' not found", type, resource);
            }
        } else {
            messageTemplate = String.join(delimiter, value);
        }
        if (messageTemplate.trim().isEmpty()) {
            throw illegalConfiguration("@%sMessage's template cannot be empty", type);
        }
        return messageTemplate;
    }

    private static String getResourceText(Class<?> clazz, String resource) {
        InputStream inputStream = clazz.getResourceAsStream(resource);
        if (inputStream == null) {
            inputStream = clazz.getResourceAsStream("/" + resource);
        }
        return getText(inputStream);
    }

    private static String getText(InputStream inputStream) {
        if (inputStream == null) {
            return null;
        }
        try (Scanner scanner = new Scanner(inputStream);
                Scanner s = scanner.useDelimiter("\\A")) {
            return s.hasNext() ? s.next() : "";
        }
    }

    private static Optional<Object> findMemoryId(Method method, Object[] args) {
        Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            if (parameters[i].isAnnotationPresent(MemoryId.class)) {
                Object memoryId = args[i];
                if (memoryId == null) {
                    throw illegalArgument(
                            "The value of parameter '%s' annotated with @MemoryId in method '%s' must not be null",
                            parameters[i].getName(), method.getName());
                }
                return Optional.of(memoryId);
            }
        }
        return Optional.empty();
    }
}
