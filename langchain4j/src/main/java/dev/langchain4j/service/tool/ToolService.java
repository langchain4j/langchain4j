package dev.langchain4j.service.tool;

import static dev.langchain4j.agent.tool.ToolSpecifications.toolSpecificationFrom;
import static dev.langchain4j.internal.Exceptions.runtime;
import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getAnnotatedMethod;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.service.IllegalConfigurationException.illegalConfiguration;
import static dev.langchain4j.service.tool.search.ToolSearchService.addFoundTools;

import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.ReturnBehavior;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.ToolArgumentsException;
import dev.langchain4j.internal.DefaultExecutorProvider;
import dev.langchain4j.invocation.InvocationContext;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
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
    private final Set<String> immediateReturnTools = new HashSet<>();
    private final Set<ToolProvider> toolProviders = new LinkedHashSet<>();
    private Executor executor;
    private int maxSequentialToolsInvocations = 100;
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
        immediateReturnTools.addAll(immediateReturnToolNames);
    }

    public void tools(Collection<Object> objectsWithTools) {
        for (Object objectWithTool : objectsWithTools) {
            if (objectWithTool instanceof Class) {
                throw illegalConfiguration("Tool '%s' must be an object, not a class", objectWithTool);
            }

            if (objectWithTool instanceof Iterable) {
                throw illegalConfiguration(
                        "Tool '%s' is an Iterable (likely a nested collection). "
                                + "Please pass tool objects directly, not wrapped in collections.",
                        objectWithTool.getClass().getName());
            }

            AtomicBoolean hasToolMethods = new AtomicBoolean(false);
            for (Method method : objectWithTool.getClass().getDeclaredMethods()) {
                getAnnotatedMethod(method, Tool.class).ifPresent(toolMethod -> {
                    hasToolMethods.set(true);
                    processToolMethod(objectWithTool, toolMethod);
                });
            }

            if (!hasToolMethods.get()) {
                throw illegalConfiguration(
                        "Object '%s' does not have any methods annotated with @Tool",
                        objectWithTool.getClass().getName());
            }
        }
    }

    private void processToolMethod(Object object, Method method) {
        ToolSpecification toolSpecification = toolSpecificationFrom(method);
        if (toolExecutors.containsKey(toolSpecification.name())) {
            throw new IllegalConfigurationException("Duplicated definition for tool: " + toolSpecification.name());
        }
        toolSpecifications.add(toolSpecification);

        ToolExecutor toolExecutor = createToolExecutor(object, method);
        toolExecutors.put(toolSpecification.name(), toolExecutor);

        if (method.getAnnotation(Tool.class).returnBehavior() == ReturnBehavior.IMMEDIATE) {
            immediateReturnTools.add(toolSpecification.name());
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

    public void maxSequentialToolsInvocations(int maxSequentialToolsInvocations) {
        this.maxSequentialToolsInvocations = maxSequentialToolsInvocations;
    }

    public int maxSequentialToolsInvocations() {
        return maxSequentialToolsInvocations;
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

    public ToolServiceContext createContext(InvocationContext invocationContext,
                                            UserMessage userMessage,
                                            List<ChatMessage> messages) {
        ToolServiceContext toolServiceContext = doCreateContext(invocationContext, userMessage, messages);
        if (toolSearchService == null) {
            return toolServiceContext;
        } else {
            return toolSearchService.adjust(toolServiceContext, messages, invocationContext);
        }
    }

    /**
     * @deprecated use {@link #createContext(InvocationContext, UserMessage, List)} instead
     */
    @Deprecated(since = "1.13.0")
    public ToolServiceContext createContext(InvocationContext invocationContext,
                                            UserMessage userMessage,
                                            ChatMemory chatMemory) {
        return createContext(invocationContext, userMessage, chatMemory.messages());
    }

    /**
     * @deprecated use {@link #createContext(InvocationContext, UserMessage, ChatMemory)} instead
     */
    @Deprecated(since = "1.12.0")
    public ToolServiceContext createContext(InvocationContext invocationContext, UserMessage userMessage) {
        return doCreateContext(invocationContext, userMessage, List.of());
    }

    private ToolServiceContext doCreateContext(InvocationContext invocationContext,
                                               UserMessage userMessage,
                                               List<ChatMessage> messages) {
        if (this.toolProviders.isEmpty()) {
            return this.toolSpecifications.isEmpty()
                    ? ToolServiceContext.Empty.INSTANCE
                    : ToolServiceContext.builder()
                            .effectiveTools(this.toolSpecifications)
                            .availableTools(this.toolSpecifications)
                            .toolExecutors(this.toolExecutors)
                            .immediateReturnTools(this.immediateReturnTools)
                            .build();
        }

        List<ToolSpecification> toolSpecifications = new ArrayList<>(this.toolSpecifications);
        Map<String, ToolExecutor> toolExecutors = new HashMap<>(this.toolExecutors);
        Set<String> immediateReturnTools = new HashSet<>(this.immediateReturnTools);
        List<ToolProvider> dynamicToolProviders = new ArrayList<>();

        ToolProviderRequest toolProviderRequest = ToolProviderRequest.builder()
                .invocationContext(invocationContext)
                .userMessage(userMessage)
                .messages(messages)
                .build();
        toolProviders.forEach(toolProvider -> {
            if (toolProvider.isDynamic()) {
                // Dynamic providers are not called here; they will be evaluated
                // before each LLM call via refreshDynamicProviders()
                dynamicToolProviders.add(toolProvider);
                return;
            }
            ToolProviderResult toolProviderResult = toolProvider.provideTools(toolProviderRequest);
            if (toolProviderResult != null) {
                for (Map.Entry<ToolSpecification, ToolExecutor> entry :
                        toolProviderResult.tools().entrySet()) {
                    String toolName = entry.getKey().name();
                    if (toolExecutors.putIfAbsent(toolName, entry.getValue()) != null) {
                        throw new IllegalConfigurationException(
                                "Duplicated definition for tool: " + toolName);
                    }
                    toolSpecifications.add(entry.getKey());
                }
                if (toolProviderResult.immediateReturnToolNames() != null) {
                    immediateReturnTools.addAll(toolProviderResult.immediateReturnToolNames());
                }
            }
        });
        return ToolServiceContext.builder()
                .effectiveTools(toolSpecifications)
                .availableTools(toolSpecifications)
                .toolExecutors(toolExecutors)
                .immediateReturnTools(immediateReturnTools)
                .dynamicToolProviders(dynamicToolProviders)
                .build();
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

        int sequentialToolsInvocationsLeft = maxSequentialToolsInvocations;
        while (true) {

            if (sequentialToolsInvocationsLeft-- == 0) {
                throw runtime(
                        "Something is wrong, exceeded %s sequential tool invocations", maxSequentialToolsInvocations);
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

            Map<ToolExecutionRequest, ToolExecutionResult> toolResults =
                    execute(aiMessage.toolExecutionRequests(), toolServiceContext.toolExecutors(), invocationContext);

            boolean immediateToolReturn = true;
            for (Map.Entry<ToolExecutionRequest, ToolExecutionResult> entry : toolResults.entrySet()) {
                ToolExecutionRequest request = entry.getKey();
                ToolExecutionResult result = entry.getValue();
                ToolExecutionResultMessage resultMessage = ToolExecutionResultMessage.builder()
                        .id(request.id())
                        .toolName(request.name())
                        .text(result.resultText())
                        .isError(result.isError())
                        .attributes(result.attributes())
                        .build();

                ToolExecution toolExecution =
                        ToolExecution.builder().request(request).result(result).build();
                toolExecutions.add(toolExecution);

                fireToolExecutedEvent(invocationContext, request, toolExecution, context.eventListenerRegistrar);

                if (chatMemory != null) {
                    chatMemory.add(resultMessage);
                } else {
                    messages.add(resultMessage);
                }

                if (immediateToolReturn) {
                    if (toolServiceContext.immediateReturnTools().contains(request.name())) {
                        if (!isReturnTypeResult) {
                            throw illegalConfiguration(
                                    "Tool '%s' with immediate return is not allowed on a AI service not returning Result.",
                                    request.name());
                        }
                    } else {
                        immediateToolReturn = false;
                    }
                }
            }

            if (immediateToolReturn) {
                ChatResponse finalResponse = intermediateResponses.remove(intermediateResponses.size() - 1);
                return ToolServiceResult.builder()
                        .intermediateResponses(intermediateResponses)
                        .finalResponse(finalResponse)
                        .toolExecutions(toolExecutions)
                        .aggregateTokenUsage(aggregateTokenUsage)
                        .immediateToolReturn(true)
                        .build();
            }

            if (chatMemory != null) {
                messages = chatMemory.messages();
            }

            ToolServiceContext refreshedContext = refreshDynamicProviders(toolServiceContext, messages, invocationContext);
            if (refreshedContext != toolServiceContext) {
                toolServiceContext = refreshedContext;
                parameters = parameters.overrideWith(ChatRequestParameters.builder()
                        .toolSpecifications(toolServiceContext.effectiveTools())
                        .build());
            } // TODO test how it works together with tool search
            if (toolSearchService != null) {
                parameters = addFoundTools(parameters, toolResults.values(), toolServiceContext.availableTools());
            }

            ChatRequest chatRequest = context.chatRequestTransformer.apply(
                    ChatRequest.builder()
                            .messages(messages)
                            .parameters(parameters)
                            .build(),
                    memoryId);

            fireRequestIssuedEvent(chatRequest, invocationContext, context.eventListenerRegistrar);
            chatResponse = context.chatModel.chat(chatRequest);
            fireResponseReceivedEvent(chatRequest, chatResponse, invocationContext, context.eventListenerRegistrar);
            aggregateTokenUsage =
                    TokenUsage.sum(aggregateTokenUsage, chatResponse.metadata().tokenUsage());
        }

        return ToolServiceResult.builder()
                .intermediateResponses(intermediateResponses)
                .finalResponse(chatResponse)
                .toolExecutions(toolExecutions)
                .aggregateTokenUsage(aggregateTokenUsage)
                .build();
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
            ToolServiceContext toolServiceContext,
            List<ChatMessage> messages,
            InvocationContext invocationContext) {
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

        List<ToolSpecification> newToolSpecs = new ArrayList<>(toolServiceContext.effectiveTools());
        Map<String, ToolExecutor> newToolExecutors = new HashMap<>(toolServiceContext.toolExecutors());
        Set<String> immediateReturnTools = new HashSet<>(toolServiceContext.immediateReturnTools());
        boolean changed = false;

        for (ToolProvider dynamicProvider : dynamicProviders) {
            ToolProviderResult result = dynamicProvider.provideTools(request);
            if (result != null) {
                for (Map.Entry<ToolSpecification, ToolExecutor> toolEntry : result.tools().entrySet()) {
                    String toolName = toolEntry.getKey().name();
                    if (!newToolExecutors.containsKey(toolName)) {
                        newToolSpecs.add(toolEntry.getKey());
                        newToolExecutors.put(toolName, toolEntry.getValue());
                        changed = true;
                    }
                }
                immediateReturnTools.addAll(result.immediateReturnToolNames());
            }
        }

        if (!changed) {
            return toolServiceContext;
        }

        return toolServiceContext.toBuilder()
                .effectiveTools(newToolSpecs)
                .availableTools(newToolSpecs)
                .toolExecutors(newToolExecutors)
                .immediateReturnTools(immediateReturnTools)
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
                .resultText(toolExecution.result())
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

    private Map<ToolExecutionRequest, ToolExecutionResult> executeConcurrently(
            List<ToolExecutionRequest> toolRequests,
            Map<String, ToolExecutor> toolExecutors,
            InvocationContext invocationContext) {
        Map<ToolExecutionRequest, CompletableFuture<ToolExecutionResult>> futures = new LinkedHashMap<>();

        for (ToolExecutionRequest toolRequest : toolRequests) {
            CompletableFuture<ToolExecutionResult> future = CompletableFuture.supplyAsync(
                    () -> {
                        ToolExecutor toolExecutor = toolExecutors.get(toolRequest.name());
                        if (toolExecutor == null) {
                            return applyToolHallucinationStrategy(toolRequest);
                        } else {
                            return executeWithErrorHandling(
                                    toolRequest,
                                    toolExecutor,
                                    invocationContext,
                                    argumentsErrorHandler(),
                                    executionErrorHandler());
                        }
                    },
                    executor);
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
            beforeToolExecution.accept(
                    BeforeToolExecution.builder().request(toolRequest).build());
        }

        ToolExecutor executor = toolExecutors.get(toolRequest.name());
        ToolExecutionResult toolResult = executor == null
                ? applyToolHallucinationStrategy(toolRequest)
                : executeWithErrorHandling(
                        toolRequest, executor, invocationContext, argumentsErrorHandler(), executionErrorHandler());

        if (afterToolExecution != null) {
            afterToolExecution.accept(ToolExecution.builder()
                    .request(toolRequest)
                    .result(toolResult)
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

    public boolean isImmediateTool(String toolName) {
        return immediateReturnTools.contains(toolName);
    }
}
