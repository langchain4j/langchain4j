package dev.langchain4j.service.tool;

import static dev.langchain4j.agent.tool.ToolSpecifications.toolSpecificationFrom;
import static dev.langchain4j.internal.Exceptions.runtime;
import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getAnnotatedMethod;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.service.IllegalConfigurationException.illegalConfiguration;

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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
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

    static final String TOOL_LIMIT_EXCEEDED_MESSAGE =
            "Tool '%s' has reached its execution limit of %d. Please proceed without it.";

    static final String TOOL_LOOP_ENDED_MESSAGE =
            "Tool loop ended because another tool reached its execution limit. Please proceed without tools.";

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

    private int defaultMaxToolExecutions = Integer.MAX_VALUE;
    private final Map<String, Integer> perToolMaxExecutions = new HashMap<>();
    private ToolLimitExceededBehavior defaultToolLimitExceededBehavior = ToolLimitExceededBehavior.CONTINUE;
    private final Map<String, ToolLimitExceededBehavior> perToolLimitExceededBehaviors = new HashMap<>();

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
        for (Object objectWithTools : objectsWithTools) {
            for (AiServiceTool tool : findTools(objectWithTools)) {
                String toolName = tool.toolSpecification().name();
                if (toolExecutors.containsKey(toolName)) {
                    throw new IllegalConfigurationException("Duplicated definition for tool: " + toolName);
                }
                toolSpecifications.add(tool.toolSpecification());
                toolExecutors.put(toolName, tool.toolExecutor());
                if (tool.immediateReturn()) {
                    immediateReturnTools.add(toolName);
                }
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
        for (Method method : objectWithTools.getClass().getDeclaredMethods()) {
            Optional<Method> annotatedMethod = getAnnotatedMethod(method, Tool.class);
            if (annotatedMethod.isPresent()) {
                Method toolMethod = annotatedMethod.get();
                result.add(AiServiceTool.builder()
                        .toolSpecification(toolSpecificationFrom(toolMethod))
                        .toolExecutor(createToolExecutor(objectWithTools, toolMethod))
                        .immediateReturn(
                                toolMethod.getAnnotation(Tool.class).returnBehavior() == ReturnBehavior.IMMEDIATE)
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

    public void maxSequentialToolsInvocations(int maxSequentialToolsInvocations) {
        this.maxSequentialToolsInvocations = maxSequentialToolsInvocations;
    }

    public int maxSequentialToolsInvocations() {
        return maxSequentialToolsInvocations;
    }

    /**
     * Applies a {@link ToolExecutionLimits} configuration: the default limit, per-tool overrides,
     * the default behavior, and per-tool behavior overrides. Replaces any previously supplied
     * limits.
     *
     * @since 1.14.0
     */
    public void toolExecutionLimits(ToolExecutionLimits limits) {
        ensureNotNull(limits, "limits");
        this.defaultMaxToolExecutions = limits.defaultLimit();
        this.perToolMaxExecutions.clear();
        this.perToolMaxExecutions.putAll(limits.perToolLimits());
        this.defaultToolLimitExceededBehavior = limits.defaultBehavior();
        this.perToolLimitExceededBehaviors.clear();
        this.perToolLimitExceededBehaviors.putAll(limits.perToolBehaviors());
    }

    public int maxToolExecutionsFor(String toolName) {
        return perToolMaxExecutions.getOrDefault(toolName, defaultMaxToolExecutions);
    }

    public ToolLimitExceededBehavior toolLimitExceededBehaviorFor(String toolName) {
        return perToolLimitExceededBehaviors.getOrDefault(toolName, defaultToolLimitExceededBehavior);
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
                dynamicToolProviders.add(toolProvider);
                return;
            }
            ToolProviderResult toolProviderResult = toolProvider.provideTools(toolProviderRequest);
            if (toolProviderResult != null) {
                for (Map.Entry<ToolSpecification, ToolExecutor> entry :
                        toolProviderResult.tools().entrySet()) {
                    String toolName = entry.getKey().name();
                    if (toolExecutors.putIfAbsent(toolName, entry.getValue()) != null) {
                        throw new IllegalConfigurationException("Duplicated definition for tool: " + toolName);
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
        Map<String, Integer> toolExecutionCounts = new HashMap<>();

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

            // Separate tool requests into within-budget and over-budget.
            // Use a projected count to handle parallel calls to the same tool in one response.
            List<ToolExecutionRequest> withinBudgetRequests = new ArrayList<>();
            Map<ToolExecutionRequest, ToolExecutionResult> overBudgetResults = new LinkedHashMap<>();
            Map<String, Integer> projectedCounts = new HashMap<>(toolExecutionCounts);
            boolean endLoop = false;

            for (ToolExecutionRequest request : aiMessage.toolExecutionRequests()) {
                String toolName = request.name();
                int currentCount = projectedCounts.getOrDefault(toolName, 0);
                int limit = maxToolExecutionsFor(toolName);

                if (currentCount >= limit) {
                    ToolLimitExceededBehavior behavior = toolLimitExceededBehaviorFor(toolName);
                    switch (behavior) {
                        case ERROR:
                            throw new ToolExecutionLimitExceededException(toolName, limit, currentCount + 1);
                        case END:
                            endLoop = true;
                            break;
                        case CONTINUE:
                            overBudgetResults.put(
                                    request,
                                    ToolExecutionResult.builder()
                                            .isError(true)
                                            .resultText(String.format(TOOL_LIMIT_EXCEEDED_MESSAGE, toolName, limit))
                                            .build());
                            break;
                    }
                    if (endLoop) {
                        break;
                    }
                } else {
                    withinBudgetRequests.add(request);
                    projectedCounts.merge(toolName, 1, Integer::sum);
                }
            }

            if (endLoop) {
                // END behavior: skip executing any tools from this response and make one
                // final LLM call with no tools, so the model can produce a text answer.
                // Add error results for each tool request so the conversation history is valid
                // (models expect a ToolExecutionResultMessage for each tool call).
                for (ToolExecutionRequest request : aiMessage.toolExecutionRequests()) {
                    String name = request.name();
                    int reqLimit = maxToolExecutionsFor(name);
                    int count = toolExecutionCounts.getOrDefault(name, 0);
                    String text = count >= reqLimit
                            ? String.format(TOOL_LIMIT_EXCEEDED_MESSAGE, name, reqLimit)
                            : TOOL_LOOP_ENDED_MESSAGE;
                    ToolExecutionResultMessage resultMessage = ToolExecutionResultMessage.builder()
                            .id(request.id())
                            .toolName(name)
                            .text(text)
                            .isError(true)
                            .build();
                    if (chatMemory != null) {
                        chatMemory.add(resultMessage);
                    } else {
                        messages.add(resultMessage);
                    }
                }

                if (chatMemory != null) {
                    messages = chatMemory.messages();
                }

                parameters = parameters.overrideWith(ChatRequestParameters.builder()
                        .toolSpecifications(List.of())
                        .build());

                ChatRequest chatRequest = context.chatRequestTransformer.apply(
                        ChatRequest.builder()
                                .messages(messages)
                                .parameters(parameters)
                                .build(),
                        memoryId);

                fireRequestIssuedEvent(chatRequest, invocationContext, context.eventListenerRegistrar);
                chatResponse = context.chatModel.chat(chatRequest);
                fireResponseReceivedEvent(chatRequest, chatResponse, invocationContext, context.eventListenerRegistrar);
                aggregateTokenUsage = TokenUsage.sum(
                        aggregateTokenUsage, chatResponse.metadata().tokenUsage());
                break;
            }

            intermediateResponses.add(chatResponse);

            // Execute within-budget tool requests
            Map<ToolExecutionRequest, ToolExecutionResult> toolResults;
            if (!withinBudgetRequests.isEmpty()) {
                toolResults = execute(withinBudgetRequests, toolServiceContext.toolExecutors(), invocationContext);
            } else {
                toolResults = new LinkedHashMap<>();
            }

            // Increment counts for executed tools
            for (ToolExecutionRequest request : withinBudgetRequests) {
                toolExecutionCounts.merge(request.name(), 1, Integer::sum);
            }

            // Merge over-budget results (preserving original request order)
            Map<ToolExecutionRequest, ToolExecutionResult> allResults = new LinkedHashMap<>();
            for (ToolExecutionRequest request : aiMessage.toolExecutionRequests()) {
                if (toolResults.containsKey(request)) {
                    allResults.put(request, toolResults.get(request));
                } else if (overBudgetResults.containsKey(request)) {
                    allResults.put(request, overBudgetResults.get(request));
                }
            }

            boolean immediateToolReturn = true;
            for (Map.Entry<ToolExecutionRequest, ToolExecutionResult> entry : allResults.entrySet()) {
                ToolExecutionRequest request = entry.getKey();
                ToolExecutionResult result = entry.getValue();
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
                        .toolExecutionCounts(toolExecutionCounts)
                        .build();
            }

            if (chatMemory != null) {
                messages = chatMemory.messages();
                if (!context.storeRetrievedContentInChatMemory) {
                    messages = UserMessage.replaceLast(chatMemory.messages(), invocationContext.userMessage());
                }
            }

            toolServiceContext = refreshDynamicProviders(toolServiceContext, messages, invocationContext);
            Set<String> exhausted = findExhaustedTools(toolExecutionCounts);
            if (toolSearchService != null) {
                toolServiceContext =
                        ToolSearchService.addFoundTools(toolServiceContext, allResults.values(), exhausted);
            }

            // Level 1: Remove exhausted tools before the next LLM call
            toolServiceContext = toolServiceContext.withoutTools(exhausted);

            // Rebuild the ToolSearchExecutor with a fresh searchableTools snapshot so the
            // tool-search strategy cannot re-surface an exhausted tool in the next round.
            if (toolSearchService != null && !exhausted.isEmpty()) {
                toolServiceContext =
                        toolSearchService.refreshSearchExecutors(toolServiceContext, invocationContext, exhausted);
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
                .toolExecutionCounts(toolExecutionCounts)
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
        Set<String> newImmediateReturnTools = new HashSet<>(toolServiceContext.immediateReturnTools());
        boolean changed = false;

        for (ToolProvider dynamicProvider : dynamicProviders) {
            ToolProviderResult result = dynamicProvider.provideTools(request);
            if (result != null) {
                for (Map.Entry<ToolSpecification, ToolExecutor> toolEntry :
                        result.tools().entrySet()) {
                    String toolName = toolEntry.getKey().name();
                    if (!newToolExecutors.containsKey(toolName)) {
                        newEffectiveTools.add(toolEntry.getKey());
                        newAvailableTools.add(toolEntry.getKey());
                        newToolExecutors.put(toolName, toolEntry.getValue());
                        changed = true;
                    }
                }
                newImmediateReturnTools.addAll(result.immediateReturnToolNames());
            }
        }

        if (!changed) {
            return toolServiceContext;
        }

        return toolServiceContext.toBuilder()
                .effectiveTools(newEffectiveTools)
                .availableTools(newAvailableTools)
                .toolExecutors(newToolExecutors)
                .immediateReturnTools(newImmediateReturnTools)
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

    /**
     * Rebuilds the tool-search executor's {@code searchableTools} snapshot to exclude
     * {@code exhaustedToolNames}. No-op if no tool-search strategy is configured or the
     * exhausted set is empty.
     *
     * @since 1.14.0
     */
    public ToolServiceContext refreshSearchExecutorsIfNeeded(
            ToolServiceContext toolServiceContext,
            InvocationContext invocationContext,
            Set<String> exhaustedToolNames) {
        if (toolSearchService == null || exhaustedToolNames.isEmpty()) {
            return toolServiceContext;
        }
        return toolSearchService.refreshSearchExecutors(toolServiceContext, invocationContext, exhaustedToolNames);
    }

    /**
     * Returns the set of tool names that have reached their execution limits.
     *
     * @since 1.14.0
     */
    public Set<String> findExhaustedTools(Map<String, Integer> toolExecutionCounts) {
        Set<String> exhausted = new HashSet<>();
        for (Map.Entry<String, Integer> entry : toolExecutionCounts.entrySet()) {
            if (entry.getValue() >= maxToolExecutionsFor(entry.getKey())) {
                exhausted.add(entry.getKey());
            }
        }
        return exhausted;
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
