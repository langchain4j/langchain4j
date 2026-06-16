package dev.langchain4j.service.tool;

import static dev.langchain4j.agent.tool.ReturnBehavior.IMMEDIATE;
import static dev.langchain4j.agent.tool.ReturnBehavior.IMMEDIATE_IF_LAST;
import static dev.langchain4j.agent.tool.ToolSpecifications.toolSpecificationFrom;
import static dev.langchain4j.internal.Exceptions.runtime;
import static dev.langchain4j.internal.Utils.allConcreteMethods;
import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getAnnotatedMethod;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.service.IllegalConfigurationException.illegalConfiguration;

import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.ReturnBehavior;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolMemoryId;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.ToolArgumentsException;
import dev.langchain4j.internal.DefaultExecutorProvider;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.invocation.LangChain4jManaged;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.observability.api.AiServiceListenerRegistrar;
import dev.langchain4j.observability.api.event.AiServiceRequestIssuedEvent;
import dev.langchain4j.observability.api.event.AiServiceResponseReceivedEvent;
import dev.langchain4j.observability.api.event.ToolExecutedEvent;
import dev.langchain4j.service.AiServiceContext;
import dev.langchain4j.service.IllegalConfigurationException;
import dev.langchain4j.service.tool.search.ToolSearchService;
import dev.langchain4j.service.tool.search.ToolSearchStrategy;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;

@Internal
public class ToolService {

    private static final ToolArgumentsErrorHandler DEFAULT_TOOL_ARGUMENTS_ERROR_HANDLER = (error, context) -> {
        if (error instanceof RuntimeException re) {
            throw re;
        } else {
            throw new RuntimeException(error);
        }
    };
    private static final ToolExecutionErrorHandler DEFAULT_TOOL_EXECUTION_ERROR_HANDLER = (error, context) -> {
        String errorMessage =
                isNullOrBlank(error.getMessage()) ? error.getClass().getName() : error.getMessage();
        return ToolErrorHandlerResult.text(errorMessage);
    };

    private final List<ToolSpecification> toolSpecifications = new ArrayList<>();
    private final Map<String, ToolExecutor> toolExecutors = new HashMap<>();
    private final Map<String, ReturnBehavior> returnBehaviors = new HashMap<>();
    private final Set<ToolProvider> toolProviders = new LinkedHashSet<>();
    private Executor executor;
    private int maxToolCallingRoundTrips = 100;
    private ToolArgumentsErrorHandler argumentsErrorHandler;
    private ToolExecutionErrorHandler executionErrorHandler;
    private Function<ToolExecutionRequest, ToolExecutionResultMessage> toolHallucinationStrategy =
            HallucinatedToolNameStrategy.THROW_EXCEPTION;
    private ToolSearchService toolSearchService;

    private Consumer<BeforeToolExecution> beforeToolExecution = null;
    private Consumer<ToolExecution> afterToolExecution = null;

    public void hallucinatedToolNameStrategy(
            Function<ToolExecutionRequest, ToolExecutionResultMessage> toolHallucinationStrategy) {
        this.toolHallucinationStrategy = toolHallucinationStrategy;
    }

    public void toolProvider(ToolProvider toolProvider) {
        if (toolProvider != null) {
            this.toolProviders.add(toolProvider);
        }
    }

    /**
     * @since 1.12.0
     */
    public void toolProviders(Collection<ToolProvider> toolProviders) {
        if (toolProviders != null) {
            this.toolProviders.addAll(toolProviders);
        }
    }

    public void tools(Map<ToolSpecification, ToolExecutor> tools) {
        tools.forEach((toolSpecification, toolExecutor) -> {
            toolSpecifications.add(toolSpecification);
            toolExecutors.put(toolSpecification.name(), toolExecutor);
        });
    }

    public void tools(Map<ToolSpecification, ToolExecutor> tools, Set<String> immediateReturnToolNames) {
        this.tools(tools);
        immediateReturnToolNames.forEach(name -> returnBehaviors.put(name, IMMEDIATE));
    }

    public void tools(Collection<Object> objectsWithTools) {
        for (Object objectWithTools : objectsWithTools) {
            List<AiServiceTool> tools = findTools(objectWithTools);
            addTools(tools, this.toolExecutors, this.toolSpecifications, this.returnBehaviors);
        }
    }

    /**
     * @since 1.14.0
     */
    public void tools(List<AiServiceTool> tools) {
        addTools(tools, this.toolExecutors, this.toolSpecifications, this.returnBehaviors);
    }

    private static void validateToolParameters(Method toolMethod) {
        for (Parameter parameter : toolMethod.getParameters()) {
            P pAnnotation = parameter.getAnnotation(P.class);
            if (pAnnotation == null) {
                continue;
            }
            Class<?> type = parameter.getType();
            boolean hasDefault = !P.NO_DEFAULT.equals(pAnnotation.defaultValue());

            if (type.isPrimitive() && !pAnnotation.required() && !hasDefault) {
                throw illegalConfiguration(
                        "Parameter '%s' of tool '%s.%s' is a primitive (%s) and cannot be marked as @P(required = false). "
                                + "Use a boxed type (e.g. Integer instead of int), Optional<T>, or @P(defaultValue = ...).",
                        parameter.getName(),
                        toolMethod.getDeclaringClass().getName(),
                        toolMethod.getName(),
                        type.getName());
            }

            if (!hasDefault) {
                continue;
            }

            if (type == Optional.class) {
                throw illegalConfiguration(
                        "Parameter '%s' of tool '%s.%s' has @P(defaultValue = ...) and is Optional<T>. "
                                + "Optional<T> already represents \"absent\"; use one mechanism or the other.",
                        parameter.getName(), toolMethod.getDeclaringClass().getName(), toolMethod.getName());
            }

            if (parameter.isAnnotationPresent(ToolMemoryId.class)
                    || InvocationParameters.class.isAssignableFrom(type)
                    || type == InvocationContext.class
                    || LangChain4jManaged.class.isAssignableFrom(type)) {
                throw illegalConfiguration(
                        "Parameter '%s' of tool '%s.%s' has @P(defaultValue = ...) but is a framework-injected parameter; "
                                + "default values are not supported on framework-injected parameters.",
                        parameter.getName(), toolMethod.getDeclaringClass().getName(), toolMethod.getName());
            }

            try {
                DefaultToolExecutor.parseDefaultValue(
                        pAnnotation.defaultValue(), parameter.getName(), type, parameter.getParameterizedType());
            } catch (Exception e) {
                throw illegalConfiguration(
                        "Cannot parse @P(defaultValue = \"%s\") for parameter '%s' of tool '%s.%s' (type %s): %s",
                        pAnnotation.defaultValue(),
                        parameter.getName(),
                        toolMethod.getDeclaringClass().getName(),
                        toolMethod.getName(),
                        type.getName(),
                        e.getMessage());
            }
        }
    }

    private static ToolExecutor createToolExecutor(Object object, Method method) {
        return DefaultToolExecutor.builder()
                .object(object)
                .originalMethod(method)
                .methodToInvoke(method)
                .wrapToolArgumentsExceptions(true)
                .propagateToolExecutionExceptions(true)
                .build();
    }

    /**
     * Scans the given object for {@link Tool @Tool}-annotated methods and returns
     * a list of {@link AiServiceTool}s.
     *
     * @param objectWithTools an object containing {@link Tool @Tool}-annotated methods
     * @return list of resolved tools
     * @throws IllegalConfigurationException if the object has no {@link Tool @Tool}-annotated methods
     * @since 1.13.0
     */
    public static List<AiServiceTool> findTools(Object objectWithTools) {
        if (objectWithTools instanceof Class) {
            throw illegalConfiguration("Tool '%s' must be an object, not a class", objectWithTools);
        }
        if (objectWithTools instanceof Iterable) {
            throw illegalConfiguration(
                    "Tool '%s' is an Iterable (likely a nested collection). "
                            + "Please pass tool objects directly, not wrapped in collections.",
                    objectWithTools.getClass().getName());
        }

        List<AiServiceTool> result = new ArrayList<>();
        for (Method method : allConcreteMethods(objectWithTools.getClass())) {
            Optional<Method> annotatedMethod = getAnnotatedMethod(method, Tool.class);
            if (annotatedMethod.isPresent()) {
                Method toolMethod = annotatedMethod.get();
                validateToolParameters(toolMethod);
                result.add(AiServiceTool.builder()
                        .toolSpecification(toolSpecificationFrom(toolMethod))
                        .toolExecutor(createToolExecutor(objectWithTools, toolMethod))
                        .returnBehavior(toolMethod.getAnnotation(Tool.class).returnBehavior())
                        .build());
            }
        }
        if (result.isEmpty()) {
            throw illegalConfiguration(
                    "Object '%s' does not have any methods annotated with @Tool",
                    objectWithTools.getClass().getName());
        }
        return result;
    }

    /**
     * @since 1.4.0
     */
    public void executeToolsConcurrently() {
        this.executor = defaultExecutor();
    }

    /**
     * @since 1.4.0
     */
    public void executeToolsConcurrently(Executor executor) {
        this.executor = getOrDefault(executor, ToolService::defaultExecutor);
    }

    private static Executor defaultExecutor() {
        return DefaultExecutorProvider.getDefaultExecutorService();
    }

    public void maxToolCallingRoundTrips(int maxToolCallingRoundTrips) {
        this.maxToolCallingRoundTrips = maxToolCallingRoundTrips;
    }

    public int maxToolCallingRoundTrips() {
        return maxToolCallingRoundTrips;
    }

    /** @deprecated Use {@link #maxToolCallingRoundTrips(int)} instead. */
    @Deprecated(since = "1.15.0")
    public void maxSequentialToolsInvocations(int maxSequentialToolsInvocations) {
        this.maxToolCallingRoundTrips = maxSequentialToolsInvocations;
    }

    /** @deprecated Use {@link #maxToolCallingRoundTrips()} instead. */
    @Deprecated(since = "1.15.0")
    public int maxSequentialToolsInvocations() {
        return maxToolCallingRoundTrips;
    }

    /**
     * @since 1.11.0
     */
    public void beforeToolExecution(Consumer<BeforeToolExecution> beforeToolExecution) {
        this.beforeToolExecution = beforeToolExecution;
    }

    /**
     * @since 1.11.0
     */
    public void afterToolExecution(Consumer<ToolExecution> afterToolExecution) {
        this.afterToolExecution = afterToolExecution;
    }

    /**
     * @since 1.4.0
     */
    public void argumentsErrorHandler(ToolArgumentsErrorHandler handler) {
        this.argumentsErrorHandler = handler;
    }

    /**
     * @since 1.4.0
     */
    public ToolArgumentsErrorHandler argumentsErrorHandler() {
        return getOrDefault(argumentsErrorHandler, DEFAULT_TOOL_ARGUMENTS_ERROR_HANDLER);
    }

    /**
     * @since 1.4.0
     */
    public void executionErrorHandler(ToolExecutionErrorHandler handler) {
        this.executionErrorHandler = handler;
    }

    /**
     * @since 1.4.0
     */
    public ToolExecutionErrorHandler executionErrorHandler() {
        return getOrDefault(executionErrorHandler, DEFAULT_TOOL_EXECUTION_ERROR_HANDLER);
    }

    /**
     * @since 1.12.0
     */
    public void toolSearchStrategy(ToolSearchStrategy toolSearchStrategy) {
        this.toolSearchService = new ToolSearchService(toolSearchStrategy);
    }

    public ToolServiceContext createContext(
            InvocationContext invocationContext, UserMessage userMessage, List<ChatMessage> messages) {
        ToolServiceContext context = createContextFromStaticToolsAndProviders(invocationContext, userMessage, messages);
        if (toolSearchService != null) {
            context = toolSearchService.adjust(context, messages, invocationContext);
        }
        context = refreshDynamicProviders(context, messages, invocationContext);
        return context;
    }

    private ToolServiceContext createContextFromStaticToolsAndProviders(
            InvocationContext invocationContext, UserMessage userMessage, List<ChatMessage> messages) {
        if (this.toolProviders.isEmpty()) {
            if (this.toolSpecifications.isEmpty()) {
                return ToolServiceContext.Empty.INSTANCE;
            }

            return ToolServiceContext.builder()
                    .effectiveTools(this.toolSpecifications)
                    .availableTools(this.toolSpecifications)
                    .toolExecutors(this.toolExecutors)
                    .returnBehaviors(this.returnBehaviors)
                    .build();
        }

        List<ToolSpecification> toolSpecifications = new ArrayList<>(this.toolSpecifications);
        Map<String, ToolExecutor> toolExecutors = new HashMap<>(this.toolExecutors);
        Map<String, ReturnBehavior> returnBehaviors = new HashMap<>(this.returnBehaviors);
        List<ToolProvider> dynamicToolProviders = new ArrayList<>();

        ToolProviderRequest toolProviderRequest = ToolProviderRequest.builder()
                .invocationContext(invocationContext)
                .userMessage(userMessage)
                .messages(messages)
                .build();
        toolProviders.forEach(toolProvider -> {
            if (toolProvider.isDynamic()) {
                dynamicToolProviders.add(toolProvider);
                return;
            }
            ToolProviderResult toolProviderResult = toolProvider.provideTools(toolProviderRequest);
            if (toolProviderResult != null) {
                addTools(toolProviderResult.aiServiceTools(), toolExecutors, toolSpecifications, returnBehaviors);
            }
        });

        return ToolServiceContext.builder()
                .effectiveTools(toolSpecifications)
                .availableTools(toolSpecifications)
                .toolExecutors(toolExecutors)
                .returnBehaviors(returnBehaviors)
                .dynamicToolProviders(dynamicToolProviders)
                .build();
    }

    private static void addTools(
            List<AiServiceTool> tools,
            Map<String, ToolExecutor> toolExecutors,
            List<ToolSpecification> toolSpecifications,
            Map<String, ReturnBehavior> returnBehaviors) {
        for (AiServiceTool tool : tools) {
            if (toolExecutors.putIfAbsent(tool.name(), tool.toolExecutor()) != null) {
                throw new IllegalConfigurationException("Duplicated definition for tool: " + tool.name());
            }
            toolSpecifications.add(tool.toolSpecification());
            returnBehaviors.put(tool.name(), tool.returnBehavior());
        }
    }

    public ToolServiceResult executeInferenceAndToolsLoop(
            AiServiceContext context,
            Object memoryId,
            ChatResponse chatResponse,
            ChatRequestParameters parameters,
            List<ChatMessage> messages,
            ChatMemory chatMemory,
            InvocationContext invocationContext,
            ToolServiceContext toolServiceContext,
            boolean isReturnTypeResult) {
        TokenUsage aggregateTokenUsage = chatResponse.metadata().tokenUsage();
        List<ToolExecution> toolExecutions = new ArrayList<>();
        List<ChatResponse> intermediateResponses = new ArrayList<>();

        int roundTripsLeft = maxToolCallingRoundTrips;
        while (true) {

            if (roundTripsLeft-- == 0) {
                throw runtime(
                        "Something is wrong, exceeded %s tool calling round trips (maxToolCallingRoundTrips)",
                        maxToolCallingRoundTrips);
            }

            AiMessage aiMessage = chatResponse.aiMessage();

            if (chatMemory != null) {
                chatMemory.add(aiMessage);
            } else {
                messages = new ArrayList<>(messages);
                messages.add(aiMessage);
            }

            if (!aiMessage.hasToolExecutionRequests()) {
                break;
            }

            intermediateResponses.add(chatResponse);

            List<ToolExecutionRequest> toolExecutionRequests = aiMessage.toolExecutionRequests();
            Map<ToolExecutionRequest, ToolExecutionResult> toolResults =
                    execute(toolExecutionRequests, toolServiceContext.toolExecutors(), invocationContext);

            ToolResultsOutcome outcome = processToolResults(
                    context,
                    toolExecutionRequests,
                    toolResults,
                    toolExecutions,
                    chatMemory,
                    messages,
                    invocationContext,
                    toolServiceContext);

            if (shouldReturnImmediately(outcome.anyToolErrored(), outcome.returnBehaviors())) {
                return immediateToolServiceResult(intermediateResponses, toolExecutions, aggregateTokenUsage);
            }

            NextChatRequest next = prepareNextChatRequest(
                    context,
                    memoryId,
                    chatMemory,
                    messages,
                    invocationContext,
                    toolServiceContext,
                    toolResults,
                    parameters);
            messages = next.messages();
            toolServiceContext = next.toolServiceContext();
            parameters = next.parameters();

            chatResponse = context.chatModel.chat(next.chatRequest());
            fireResponseReceivedEvent(
                    next.chatRequest(), chatResponse, invocationContext, context.eventListenerRegistrar);
            aggregateTokenUsage =
                    TokenUsage.sum(aggregateTokenUsage, chatResponse.metadata().tokenUsage());
        }

        return finalToolServiceResult(chatResponse, intermediateResponses, toolExecutions, aggregateTokenUsage);
    }

    /**
     * Non-blocking counterpart of
     * {@link #executeInferenceAndToolsLoop(AiServiceContext, Object, ChatResponse, ChatRequestParameters, List, ChatMemory, InvocationContext, ToolServiceContext, boolean)}.
     * <p>
     * Re-invokes the model via {@link dev.langchain4j.model.chat.ChatModel#chatAsync(ChatRequest)} and composes
     * tool executions without blocking: no thread waits while a model response is in flight.
     * When tools are configured to execute concurrently, their results are composed (not joined) as well.
     * When no tool executor is configured, tools run on the thread that delivered the model response.
     *
     * @since 1.17.0
     */
    public CompletableFuture<ToolServiceResult> executeInferenceAndToolsLoopAsync(
            AiServiceContext context,
            Object memoryId,
            ChatResponse chatResponse,
            ChatRequestParameters parameters,
            List<ChatMessage> messages,
            ChatMemory chatMemory,
            InvocationContext invocationContext,
            ToolServiceContext toolServiceContext) {
        return executeInferenceAndToolsLoopAsync(
                context,
                memoryId,
                chatResponse,
                parameters,
                messages,
                chatMemory,
                invocationContext,
                toolServiceContext,
                chatResponse.metadata().tokenUsage(),
                new ArrayList<>(),
                new ArrayList<>(),
                maxToolCallingRoundTrips);
    }

    private CompletableFuture<ToolServiceResult> executeInferenceAndToolsLoopAsync(
            AiServiceContext context,
            Object memoryId,
            ChatResponse chatResponse,
            ChatRequestParameters parameters,
            List<ChatMessage> messages,
            ChatMemory chatMemory,
            InvocationContext invocationContext,
            ToolServiceContext toolServiceContext,
            TokenUsage aggregateTokenUsage,
            List<ToolExecution> toolExecutions,
            List<ChatResponse> intermediateResponses,
            int roundTripsLeft) {

        if (roundTripsLeft == 0) {
            return CompletableFuture.failedFuture(runtime(
                    "Something is wrong, exceeded %s tool calling round trips (maxToolCallingRoundTrips)",
                    maxToolCallingRoundTrips));
        }

        AiMessage aiMessage = chatResponse.aiMessage();

        if (chatMemory != null) {
            chatMemory.add(aiMessage);
        } else {
            messages = new ArrayList<>(messages);
            messages.add(aiMessage);
        }

        if (!aiMessage.hasToolExecutionRequests()) {
            return CompletableFuture.completedFuture(
                    finalToolServiceResult(chatResponse, intermediateResponses, toolExecutions, aggregateTokenUsage));
        }

        intermediateResponses.add(chatResponse);

        List<ToolExecutionRequest> toolExecutionRequests = aiMessage.toolExecutionRequests();
        List<ChatMessage> currentMessages = messages;
        return executeToolsAsync(toolExecutionRequests, toolServiceContext.toolExecutors(), invocationContext)
                .thenCompose(toolResults -> {
                    ToolResultsOutcome outcome = processToolResults(
                            context,
                            toolExecutionRequests,
                            toolResults,
                            toolExecutions,
                            chatMemory,
                            currentMessages,
                            invocationContext,
                            toolServiceContext);

                    if (shouldReturnImmediately(outcome.anyToolErrored(), outcome.returnBehaviors())) {
                        return CompletableFuture.completedFuture(immediateToolServiceResult(
                                intermediateResponses, toolExecutions, aggregateTokenUsage));
                    }

                    NextChatRequest next = prepareNextChatRequest(
                            context,
                            memoryId,
                            chatMemory,
                            currentMessages,
                            invocationContext,
                            toolServiceContext,
                            toolResults,
                            parameters);

                    return context.chatModel.chatAsync(next.chatRequest()).thenCompose(nextChatResponse -> {
                        fireResponseReceivedEvent(
                                next.chatRequest(),
                                nextChatResponse,
                                invocationContext,
                                context.eventListenerRegistrar);
                        return executeInferenceAndToolsLoopAsync(
                                context,
                                memoryId,
                                nextChatResponse,
                                next.parameters(),
                                next.messages(),
                                chatMemory,
                                invocationContext,
                                next.toolServiceContext(),
                                TokenUsage.sum(
                                        aggregateTokenUsage,
                                        nextChatResponse.metadata().tokenUsage()),
                                toolExecutions,
                                intermediateResponses,
                                roundTripsLeft - 1);
                    });
                });
    }

    private ToolResultsOutcome processToolResults(
            AiServiceContext context,
            List<ToolExecutionRequest> toolExecutionRequests,
            Map<ToolExecutionRequest, ToolExecutionResult> toolResults,
            List<ToolExecution> toolExecutions,
            ChatMemory chatMemory,
            List<ChatMessage> messages,
            InvocationContext invocationContext,
            ToolServiceContext toolServiceContext) {

        boolean anyToolErrored = false;
        List<ReturnBehavior> returnBehaviors = new ArrayList<>(toolExecutionRequests.size());

        for (ToolExecutionRequest request : toolExecutionRequests) {
            ToolExecutionResult result = toolResults.get(request);
            ToolExecutionResultMessage resultMessage = toResultMessage(request, result);

            ToolExecution toolExecution = ToolExecution.builder()
                    .request(request)
                    .result(result)
                    .invocationContext(invocationContext)
                    .build();
            toolExecutions.add(toolExecution);

            fireToolExecutedEvent(invocationContext, request, toolExecution, context.eventListenerRegistrar);

            if (chatMemory != null) {
                chatMemory.add(resultMessage);
            } else {
                messages.add(resultMessage);
            }

            anyToolErrored = anyToolErrored || result.isError();
            returnBehaviors.add(toolServiceContext.returnBehavior(request.name()));
        }

        return new ToolResultsOutcome(anyToolErrored, returnBehaviors);
    }

    private NextChatRequest prepareNextChatRequest(
            AiServiceContext context,
            Object memoryId,
            ChatMemory chatMemory,
            List<ChatMessage> messages,
            InvocationContext invocationContext,
            ToolServiceContext toolServiceContext,
            Map<ToolExecutionRequest, ToolExecutionResult> toolResults,
            ChatRequestParameters parameters) {

        if (chatMemory != null) {
            messages = chatMemory.messages();
            if (!context.storeRetrievedContentInChatMemory) {
                messages = UserMessage.replaceLast(chatMemory.messages(), invocationContext.userMessage());
            }
        }

        toolServiceContext = refreshDynamicProviders(toolServiceContext, messages, invocationContext);
        if (toolSearchService != null) {
            toolServiceContext = ToolSearchService.addFoundTools(toolServiceContext, toolResults.values());
        }
        parameters = parameters.overrideWith(ChatRequestParameters.builder()
                .toolSpecifications(toolServiceContext.effectiveTools())
                .build());

        ChatRequest chatRequest = context.chatRequestTransformer.apply(
                ChatRequest.builder()
                        .messages(messages)
                        .parameters(parameters)
                        .build(),
                memoryId);

        fireRequestIssuedEvent(chatRequest, invocationContext, context.eventListenerRegistrar);

        return new NextChatRequest(chatRequest, messages, toolServiceContext, parameters);
    }

    private static ToolServiceResult immediateToolServiceResult(
            List<ChatResponse> intermediateResponses,
            List<ToolExecution> toolExecutions,
            TokenUsage aggregateTokenUsage) {
        ChatResponse finalResponse = intermediateResponses.remove(intermediateResponses.size() - 1);
        return ToolServiceResult.builder()
                .intermediateResponses(intermediateResponses)
                .finalResponse(finalResponse)
                .toolExecutions(toolExecutions)
                .aggregateTokenUsage(aggregateTokenUsage)
                .immediateToolReturn(true)
                .build();
    }

    private static ToolServiceResult finalToolServiceResult(
            ChatResponse finalResponse,
            List<ChatResponse> intermediateResponses,
            List<ToolExecution> toolExecutions,
            TokenUsage aggregateTokenUsage) {
        return ToolServiceResult.builder()
                .intermediateResponses(intermediateResponses)
                .finalResponse(finalResponse)
                .toolExecutions(toolExecutions)
                .aggregateTokenUsage(aggregateTokenUsage)
                .build();
    }

    private record ToolResultsOutcome(boolean anyToolErrored, List<ReturnBehavior> returnBehaviors) {}

    private record NextChatRequest(
            ChatRequest chatRequest,
            List<ChatMessage> messages,
            ToolServiceContext toolServiceContext,
            ChatRequestParameters parameters) {}

    public static boolean shouldReturnImmediately(boolean anyToolErrored, List<ReturnBehavior> returnBehaviors) {
        if (anyToolErrored) {
            return false; // if any tool call failed, LLM should receive an error so that it can attempt to fix it
        }
        if (returnBehaviors.isEmpty()) {
            return false;
        }
        if (returnBehaviors.get(returnBehaviors.size() - 1) == IMMEDIATE_IF_LAST) {
            return true;
        }
        return returnBehaviors.stream().allMatch(rb -> rb == IMMEDIATE || rb == IMMEDIATE_IF_LAST);
    }

    /**
     * Re-evaluates {@linkplain ToolProvider#isDynamic() dynamic} tool providers and returns
     * an updated {@link ToolServiceContext} with any newly provided tool specifications and executors.
     * <p>
     * Non-dynamic providers are not re-called — their tools remain unchanged.
     * Tools returned by dynamic providers are only added, never removed: once a tool
     * is present in the context, it stays for the remainder of the AI service invocation.
     *
     * @since 1.13.0
     */
    public static ToolServiceContext refreshDynamicProviders(
            ToolServiceContext toolServiceContext, List<ChatMessage> messages, InvocationContext invocationContext) {
        if (toolServiceContext == null) {
            return null;
        }

        List<ToolProvider> dynamicProviders = toolServiceContext.dynamicToolProviders();
        if (dynamicProviders.isEmpty()) {
            return toolServiceContext;
        }

        UserMessage userMessage = UserMessage.findLast(messages).orElseThrow();
        ToolProviderRequest request = ToolProviderRequest.builder()
                .invocationContext(invocationContext)
                .userMessage(userMessage)
                .messages(messages)
                .build();

        List<ToolSpecification> newEffectiveTools = new ArrayList<>(toolServiceContext.effectiveTools());
        List<ToolSpecification> newAvailableTools = new ArrayList<>(toolServiceContext.availableTools());
        Map<String, ToolExecutor> newToolExecutors = new HashMap<>(toolServiceContext.toolExecutors());
        Map<String, ReturnBehavior> newReturnBehaviors = new HashMap<>(toolServiceContext.returnBehaviors());
        boolean changed = false;

        for (ToolProvider dynamicProvider : dynamicProviders) {
            ToolProviderResult result = dynamicProvider.provideTools(request);
            if (result != null) {
                for (AiServiceTool tool : result.aiServiceTools()) {
                    if (!newToolExecutors.containsKey(tool.name())) {
                        newEffectiveTools.add(tool.toolSpecification());
                        newAvailableTools.add(tool.toolSpecification());
                        newToolExecutors.put(tool.name(), tool.toolExecutor());
                        newReturnBehaviors.put(tool.name(), tool.returnBehavior());
                        changed = true;
                    }
                }
            }
        }

        if (!changed) {
            return toolServiceContext;
        }

        return toolServiceContext.toBuilder()
                .effectiveTools(newEffectiveTools)
                .availableTools(newAvailableTools)
                .toolExecutors(newToolExecutors)
                .returnBehaviors(newReturnBehaviors)
                .build();
    }

    private void fireToolExecutedEvent(
            InvocationContext invocationContext,
            ToolExecutionRequest request,
            ToolExecution toolExecution,
            AiServiceListenerRegistrar listenerRegistrar) {
        listenerRegistrar.fireEvent(ToolExecutedEvent.builder()
                .invocationContext(invocationContext)
                .request(request)
                .resultContents(toolExecution.resultContents())
                .build());
    }

    private void fireRequestIssuedEvent(
            ChatRequest chatRequest,
            InvocationContext invocationContext,
            AiServiceListenerRegistrar listenerRegistrar) {
        listenerRegistrar.fireEvent(AiServiceRequestIssuedEvent.builder()
                .invocationContext(invocationContext)
                .request(chatRequest)
                .build());
    }

    private void fireResponseReceivedEvent(
            ChatRequest chatRequest,
            ChatResponse chatResponse,
            InvocationContext invocationContext,
            AiServiceListenerRegistrar listenerRegistrar) {

        listenerRegistrar.fireEvent(AiServiceResponseReceivedEvent.builder()
                .invocationContext(invocationContext)
                .request(chatRequest)
                .response(chatResponse)
                .build());
    }

    private Map<ToolExecutionRequest, ToolExecutionResult> execute(
            List<ToolExecutionRequest> toolRequests,
            Map<String, ToolExecutor> toolExecutors,
            InvocationContext invocationContext) {
        if (executor != null && toolRequests.size() > 1) {
            return executeConcurrently(toolRequests, toolExecutors, invocationContext);
        } else {
            // when there is only one tool to execute, it doesn't make sense to do it in a separate thread
            return executeSequentially(toolRequests, toolExecutors, invocationContext);
        }
    }

    /**
     * Non-blocking counterpart of {@link #execute(List, Map, InvocationContext)}, built on
     * {@link ToolExecutor#executeAsync(ToolExecutionRequest, InvocationContext)} so that tools backed by
     * an asynchronous client never hold a thread while their result is in flight.
     * <p>
     * When a tool executor is configured (see {@link #executeToolsConcurrently()}), every tool execution
     * (even a single one) is initiated on it, so the thread that delivered the model response is never
     * blocked by a synchronous tool. When no executor is configured, tools are executed one after another,
     * initiated on the current thread, and a failure aborts the remaining ones — mirroring the synchronous
     * path.
     */
    private CompletableFuture<Map<ToolExecutionRequest, ToolExecutionResult>> executeToolsAsync(
            List<ToolExecutionRequest> toolRequests,
            Map<String, ToolExecutor> toolExecutors,
            InvocationContext invocationContext) {
        if (executor == null) {
            CompletableFuture<Map<ToolExecutionRequest, ToolExecutionResult>> chainedFuture =
                    CompletableFuture.completedFuture(new LinkedHashMap<>());
            for (ToolExecutionRequest toolRequest : toolRequests) {
                chainedFuture = chainedFuture.thenCompose(
                        toolResults -> executeToolAsync(invocationContext, toolExecutors, toolRequest)
                                .thenApply(toolResult -> {
                                    toolResults.put(toolRequest, toolResult);
                                    return toolResults;
                                }));
            }
            return chainedFuture;
        }

        Map<ToolExecutionRequest, CompletableFuture<ToolExecutionResult>> futures = new LinkedHashMap<>();
        for (ToolExecutionRequest toolRequest : toolRequests) {
            CompletableFuture<ToolExecutionResult> future = CompletableFuture.supplyAsync(
                            () -> executeToolAsync(invocationContext, toolExecutors, toolRequest), executor)
                    .thenCompose(toolResultFuture -> toolResultFuture);
            futures.put(toolRequest, future);
        }

        return CompletableFuture.allOf(futures.values().toArray(new CompletableFuture[0]))
                // wait for ALL futures to complete (normally or not), then handle failures below,
                // in request order - allOf alone would surface an arbitrary failure
                .handle((ignored, ignoredError) -> ignored)
                .thenCompose(ignored -> {
                    Map<ToolExecutionRequest, ToolExecutionResult> toolResults = new LinkedHashMap<>();
                    for (Map.Entry<ToolExecutionRequest, CompletableFuture<ToolExecutionResult>> entry :
                            futures.entrySet()) {
                        if (entry.getValue().isCompletedExceptionally()) {
                            // surface the first failure in request order, mirroring the synchronous path
                            return entry.getValue().thenApply(ignoredResult -> toolResults);
                        }
                        // getNow never blocks: all futures have already completed at this point
                        toolResults.put(entry.getKey(), entry.getValue().getNow(null));
                    }
                    return CompletableFuture.completedFuture(toolResults);
                });
    }

    private CompletableFuture<ToolExecutionResult> executeToolAsync(
            InvocationContext invocationContext,
            Map<String, ToolExecutor> toolExecutors,
            ToolExecutionRequest toolRequest) {
        return internalExecuteToolAsync(
                invocationContext, toolExecutors, toolRequest, this.beforeToolExecution, this.afterToolExecution);
    }

    private CompletableFuture<ToolExecutionResult> internalExecuteToolAsync(
            InvocationContext invocationContext,
            Map<String, ToolExecutor> toolExecutors,
            ToolExecutionRequest toolRequest,
            Consumer<BeforeToolExecution> beforeToolExecution,
            Consumer<ToolExecution> afterToolExecution) {
        if (beforeToolExecution != null) {
            beforeToolExecution.accept(BeforeToolExecution.builder()
                    .request(toolRequest)
                    .invocationContext(invocationContext)
                    .build());
        }

        LocalDateTime startTime = LocalDateTime.now();

        ToolExecutor toolExecutor = toolExecutors.get(toolRequest.name());
        CompletableFuture<ToolExecutionResult> futureToolResult;
        if (toolExecutor == null) {
            try {
                futureToolResult = CompletableFuture.completedFuture(applyToolHallucinationStrategy(toolRequest));
            } catch (Exception e) {
                futureToolResult = CompletableFuture.failedFuture(e);
            }
        } else {
            futureToolResult = executeWithErrorHandlingAsync(
                    toolRequest, toolExecutor, invocationContext, argumentsErrorHandler(), executionErrorHandler());
        }

        if (afterToolExecution != null) {
            futureToolResult = futureToolResult.thenApply(toolResult -> {
                afterToolExecution.accept(ToolExecution.builder()
                        .request(toolRequest)
                        .result(toolResult)
                        .startTime(startTime)
                        .finishTime(LocalDateTime.now())
                        .invocationContext(invocationContext)
                        .build());
                return toolResult;
            });
        }
        return futureToolResult;
    }

    /**
     * Non-blocking counterpart of
     * {@link #executeWithErrorHandling(ToolExecutionRequest, ToolExecutor, InvocationContext, ToolArgumentsErrorHandler, ToolExecutionErrorHandler)}.
     * Applies the error handlers both to exceptions thrown synchronously from
     * {@link ToolExecutor#executeAsync(ToolExecutionRequest, InvocationContext)} and to failed futures.
     */
    private static CompletableFuture<ToolExecutionResult> executeWithErrorHandlingAsync(
            ToolExecutionRequest toolRequest,
            ToolExecutor toolExecutor,
            InvocationContext invocationContext,
            ToolArgumentsErrorHandler argumentsErrorHandler,
            ToolExecutionErrorHandler executionErrorHandler) {
        CompletableFuture<ToolExecutionResult> futureToolResult;
        try {
            futureToolResult = toolExecutor.executeAsync(toolRequest, invocationContext);
        } catch (UnsupportedOperationException e) {
            // a configuration gap (the tool executor does not support asynchronous execution), not a tool
            // failure: fail the AI Service invocation instead of routing it to the tool error handlers,
            // which would send the message to the LLM and hide the problem from the developer
            return CompletableFuture.failedFuture(e);
        } catch (Exception e) {
            return handleToolError(e, toolRequest, invocationContext, argumentsErrorHandler, executionErrorHandler);
        }
        return futureToolResult.exceptionallyCompose(error -> {
            Throwable cause =
                    error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
            Exception exception = cause instanceof Exception e ? e : new RuntimeException(cause);
            return handleToolError(
                    exception, toolRequest, invocationContext, argumentsErrorHandler, executionErrorHandler);
        });
    }

    private static CompletableFuture<ToolExecutionResult> handleToolError(
            Exception e,
            ToolExecutionRequest toolRequest,
            InvocationContext invocationContext,
            ToolArgumentsErrorHandler argumentsErrorHandler,
            ToolExecutionErrorHandler executionErrorHandler) {
        try {
            ToolErrorContext errorContext = ToolErrorContext.builder()
                    .toolExecutionRequest(toolRequest)
                    .invocationContext(invocationContext)
                    .build();

            ToolErrorHandlerResult errorHandlerResult;
            if (e instanceof ToolArgumentsException) {
                errorHandlerResult = argumentsErrorHandler.handle(getCause(e), errorContext);
            } else {
                errorHandlerResult = executionErrorHandler.handle(getCause(e), errorContext);
            }

            return CompletableFuture.completedFuture(ToolExecutionResult.builder()
                    .isError(true)
                    .resultText(errorHandlerResult.text())
                    .build());
        } catch (Exception handlerException) {
            return CompletableFuture.failedFuture(handlerException);
        }
    }

    private Map<ToolExecutionRequest, ToolExecutionResult> executeConcurrently(
            List<ToolExecutionRequest> toolRequests,
            Map<String, ToolExecutor> toolExecutors,
            InvocationContext invocationContext) {
        Map<ToolExecutionRequest, CompletableFuture<ToolExecutionResult>> futures = new LinkedHashMap<>();

        for (ToolExecutionRequest toolRequest : toolRequests) {
            CompletableFuture<ToolExecutionResult> future = CompletableFuture.supplyAsync(
                    () -> executeTool(invocationContext, toolExecutors, toolRequest), executor);
            futures.put(toolRequest, future);
        }

        Map<ToolExecutionRequest, ToolExecutionResult> results = new LinkedHashMap<>();
        for (Map.Entry<ToolExecutionRequest, CompletableFuture<ToolExecutionResult>> entry : futures.entrySet()) {
            try {
                results.put(entry.getKey(), entry.getValue().get());
            } catch (ExecutionException e) {
                if (e.getCause() instanceof RuntimeException re) {
                    throw re;
                } else {
                    throw new RuntimeException(e.getCause());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }

        return results;
    }

    private Map<ToolExecutionRequest, ToolExecutionResult> executeSequentially(
            List<ToolExecutionRequest> toolRequests,
            Map<String, ToolExecutor> toolExecutors,
            InvocationContext invocationContext) {
        Map<ToolExecutionRequest, ToolExecutionResult> toolResults = new LinkedHashMap<>();
        for (ToolExecutionRequest toolRequest : toolRequests) {
            toolResults.put(toolRequest, executeTool(invocationContext, toolExecutors, toolRequest));
        }
        return toolResults;
    }

    private ToolExecutionResult executeTool(
            InvocationContext invocationContext,
            Map<String, ToolExecutor> toolExecutors,
            ToolExecutionRequest toolRequest) {
        return internalExecuteTool(
                invocationContext, toolExecutors, toolRequest, this.beforeToolExecution, this.afterToolExecution);
    }

    public ToolExecutionResult executeTool(
            InvocationContext invocationContext,
            Map<String, ToolExecutor> toolExecutors,
            ToolExecutionRequest toolRequest,
            Consumer<BeforeToolExecution> externalBeforeToolExecution,
            Consumer<ToolExecution> externalAfterToolExecution) {
        return internalExecuteTool(
                invocationContext,
                toolExecutors,
                toolRequest,
                nullSafeCombineConsumers(this.beforeToolExecution, externalBeforeToolExecution),
                nullSafeCombineConsumers(this.afterToolExecution, externalAfterToolExecution));
    }

    private static <T> Consumer<T> nullSafeCombineConsumers(Consumer<T> first, Consumer<T> second) {
        if (first != null && second != null) {
            return first.andThen(second);
        }
        return first != null ? first : second;
    }

    private ToolExecutionResult internalExecuteTool(
            InvocationContext invocationContext,
            Map<String, ToolExecutor> toolExecutors,
            ToolExecutionRequest toolRequest,
            Consumer<BeforeToolExecution> beforeToolExecution,
            Consumer<ToolExecution> afterToolExecution) {
        if (beforeToolExecution != null) {
            beforeToolExecution.accept(BeforeToolExecution.builder()
                    .request(toolRequest)
                    .invocationContext(invocationContext)
                    .build());
        }

        LocalDateTime startTime = LocalDateTime.now();

        ToolExecutor executor = toolExecutors.get(toolRequest.name());
        ToolExecutionResult toolResult = executor == null
                ? applyToolHallucinationStrategy(toolRequest)
                : executeWithErrorHandling(
                        toolRequest, executor, invocationContext, argumentsErrorHandler(), executionErrorHandler());

        if (afterToolExecution != null) {
            afterToolExecution.accept(ToolExecution.builder()
                    .request(toolRequest)
                    .result(toolResult)
                    .startTime(startTime)
                    .finishTime(LocalDateTime.now())
                    .invocationContext(invocationContext)
                    .build());
        }
        return toolResult;
    }

    public static ToolExecutionResult executeWithErrorHandling(
            ToolExecutionRequest toolRequest,
            ToolExecutor toolExecutor,
            InvocationContext invocationContext,
            ToolArgumentsErrorHandler argumentsErrorHandler,
            ToolExecutionErrorHandler executionErrorHandler) {
        try {
            return toolExecutor.executeWithContext(toolRequest, invocationContext);
        } catch (Exception e) {
            ToolErrorContext errorContext = ToolErrorContext.builder()
                    .toolExecutionRequest(toolRequest)
                    .invocationContext(invocationContext)
                    .build();

            ToolErrorHandlerResult errorHandlerResult;
            if (e instanceof ToolArgumentsException) {
                errorHandlerResult = argumentsErrorHandler.handle(getCause(e), errorContext);
            } else {
                errorHandlerResult = executionErrorHandler.handle(getCause(e), errorContext);
            }

            return ToolExecutionResult.builder()
                    .isError(true)
                    .resultText(errorHandlerResult.text())
                    .build();
        }
    }

    static ToolExecutionResultMessage toResultMessage(ToolExecutionRequest request, ToolExecutionResult result) {
        return ToolExecutionResultMessage.builder()
                .id(request.id())
                .toolName(request.name())
                .contents(result.resultContents())
                .isError(result.isError())
                .attributes(result.attributes())
                .build();
    }

    private static Throwable getCause(Exception e) {
        Throwable cause = e.getCause();
        return cause != null ? cause : e;
    }

    public ToolExecutionResult applyToolHallucinationStrategy(ToolExecutionRequest toolRequest) {
        ToolExecutionResultMessage toolResultMessage = toolHallucinationStrategy.apply(toolRequest);
        return ToolExecutionResult.builder()
                .resultText(toolResultMessage.text())
                .build();
    }

    public List<ToolSpecification> toolSpecifications() {
        return toolSpecifications;
    }

    public Map<String, ToolExecutor> toolExecutors() {
        return toolExecutors;
    }

    /**
     * @since 1.4.0
     */
    public Executor executor() {
        return executor;
    }

    /**
     * @since 1.12.0
     */
    public Set<ToolProvider> toolProviders() {
        return copy(toolProviders);
    }

    /**
     * @deprecated use {@link #toolProviders()} instead
     */
    @Deprecated(since = "1.12.0")
    public ToolProvider toolProvider() {
        if (toolProviders.size() == 1) {
            return toolProviders.iterator().next();
        }
        if (toolProviders.isEmpty()) {
            return null;
        }
        throw new IllegalStateException("There are multiple ToolProvider configured, use toolProviders() instead");
    }

    /**
     * Returns the effective {@link ReturnBehavior} for the given tool, as configured on this service.
     * Unknown tools and tools without an explicit behavior default to {@link ReturnBehavior#TO_LLM}.
     *
     * @since 1.14.0
     */
    public ReturnBehavior returnBehavior(String toolName) {
        return returnBehaviors.getOrDefault(toolName, ReturnBehavior.TO_LLM);
    }

    /**
     * @deprecated use {@link #returnBehavior(String)} instead
     */
    @Deprecated(since = "1.14.0")
    public boolean isImmediateTool(String toolName) {
        return returnBehaviors.get(toolName) == IMMEDIATE;
    }
}
