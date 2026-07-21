package dev.langchain4j.service.tool;

import dev.langchain4j.exception.AsyncNotSupportedException;
import dev.langchain4j.exception.UnsupportedFeatureException;
import static dev.langchain4j.agent.tool.ReturnBehavior.IMMEDIATE;
import static dev.langchain4j.agent.tool.ReturnBehavior.IMMEDIATE_IF_LAST;
import static dev.langchain4j.agent.tool.ToolSpecifications.toolSpecificationFrom;
import static dev.langchain4j.internal.Exceptions.runtime;
import static dev.langchain4j.internal.Exceptions.unwrapCompletionException;
import static dev.langchain4j.internal.Utils.allConcreteMethods;
import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getAnnotatedMethod;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.service.IllegalConfigurationException.illegalConfiguration;

import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.ReturnBehavior;
import dev.langchain4j.agent.tool.CompensateFor;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolMemoryId;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.TextContent;
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
import dev.langchain4j.observability.api.event.CompensationReason;
import dev.langchain4j.observability.api.event.ToolCompensatedEvent;
import dev.langchain4j.observability.api.event.ToolExecutedEvent;
import dev.langchain4j.service.AiServiceContext;
import dev.langchain4j.service.IllegalConfigurationException;
import dev.langchain4j.service.tool.search.ToolSearchService;
import dev.langchain4j.service.tool.search.ToolSearchStrategy;
import java.lang.reflect.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.reflect.Parameter;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

@Internal
public class ToolService {

    private static final Logger log = LoggerFactory.getLogger(ToolService.class);

    private static final ToolArgumentsErrorHandler RETHROW_ARGUMENTS_ERROR = (error, context) -> {
        if (error instanceof RuntimeException re) {
            throw re;
        } else {
            throw new RuntimeException(error);
        }
    };
    private static final ToolExecutionErrorHandler RETHROW_EXECUTION_ERROR = (error, context) -> {
        if (error instanceof RuntimeException re) {
            throw re;
        } else {
            throw new RuntimeException(error);
        }
    };
    private static final ToolArgumentsErrorHandler ARGUMENTS_ERROR_TO_LLM =
            (error, context) -> ToolErrorHandlerResult.text(errorText(error));
    private static final ToolExecutionErrorHandler EXECUTION_ERROR_TO_LLM = (error, context) -> {
        String errorMessage = errorText(error);
        log.warn(
                "Tool '{}' execution failed. The error message is being returned to the LLM. "
                        + "To customize this behavior (and silence this log), configure a custom "
                        + "ToolExecutionErrorHandler via AiServices.toolExecutionErrorHandler(...). Error: {}",
                context.toolExecutionRequest().name(),
                errorMessage,
                error);
        return ToolErrorHandlerResult.text(errorMessage);
    };

    // Default tool-error handling differs by AI Service mode:
    //  - Legacy (sync, TokenStream): argument errors fail the invocation; execution errors go to the LLM.
    //  - New async modes (CompletableFuture, reactive Flow.Publisher): reversed — argument errors (the LLM's
    //    fault, recoverable) go to the LLM so it can retry; execution errors (a real failure) fail the
    //    invocation, surfacing the problem instead of hiding it from the developer.
    private static final ToolArgumentsErrorHandler DEFAULT_TOOL_ARGUMENTS_ERROR_HANDLER = RETHROW_ARGUMENTS_ERROR;
    private static final ToolExecutionErrorHandler DEFAULT_TOOL_EXECUTION_ERROR_HANDLER = EXECUTION_ERROR_TO_LLM;
    private static final ToolArgumentsErrorHandler DEFAULT_ASYNC_TOOL_ARGUMENTS_ERROR_HANDLER = ARGUMENTS_ERROR_TO_LLM;
    private static final ToolExecutionErrorHandler DEFAULT_ASYNC_TOOL_EXECUTION_ERROR_HANDLER = RETHROW_EXECUTION_ERROR;

    private static String errorText(Throwable error) {
        return isNullOrBlank(error.getMessage()) ? error.getClass().getName() : error.getMessage();
    }

    private final List<ToolSpecification> toolSpecifications = new ArrayList<>();
    private final Map<String, ToolExecutor> toolExecutors = new HashMap<>();
    private final Map<String, ReturnBehavior> returnBehaviors = new HashMap<>();
    private final Map<String, BiFunction<ToolExecution, InvocationContext, CompletableFuture<Void>>>
            compensatingExecutors = new HashMap<>();
    private IllegalConfigurationException compensatingToolMisconfiguration;
    private final Set<ToolProvider> toolProviders = new LinkedHashSet<>();
    private boolean compensateOnToolErrors;
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
            this.compensatingExecutors.putAll(findCompensatingActions(objectWithTools));
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

    public void compensateOnToolErrors(boolean compensateOnToolErrors) {
        this.compensateOnToolErrors = compensateOnToolErrors;
        if (compensateOnToolErrors && compensatingToolMisconfiguration != null) {
            throw compensatingToolMisconfiguration;
        }
    }

    private Map<String, BiFunction<ToolExecution, InvocationContext, CompletableFuture<Void>>>
            findCompensatingActions(Object objectWithTools) {
        Map<String, BiFunction<ToolExecution, InvocationContext, CompletableFuture<Void>>> compensatingActions =
                new HashMap<>();
        if (compensatingToolMisconfiguration != null) {
            return compensatingActions;
        }

        for (Method method : allConcreteMethods(objectWithTools.getClass())) {
            CompensateFor compensateFor = method.getAnnotation(CompensateFor.class);
            if (compensateFor != null) {
                String toolName = compensateFor.value();
                ToolExecutor toolExecutor = toolExecutors.get(toolName);
                if (toolExecutor == null) {
                    compensatingToolMisconfiguration = illegalConfiguration(
                            "@CompensateFor(\"%s\") on method '%s.%s' references tool '%s' which does not exist",
                            toolName, objectWithTools.getClass().getName(), method.getName(), toolName);
                    if (compensateOnToolErrors) {
                        throw compensatingToolMisconfiguration;
                    }
                    break;
                }
                if (!(toolExecutor instanceof DefaultToolExecutor)) {
                    compensatingToolMisconfiguration = illegalConfiguration(
                            "@CompensateFor(\"%s\") on method '%s.%s' references tool '%s' which is not a @Tool-annotated method."
                                    + " Only @Tool-annotated methods support compensating actions",
                            toolName, objectWithTools.getClass().getName(), method.getName(), toolName);
                    if (compensateOnToolErrors) {
                        throw compensatingToolMisconfiguration;
                    }
                    break;
                }
                Method toolMethod = ((DefaultToolExecutor) toolExecutor).originalMethod();
                Class<?>[] compensatingParams = method.getParameterTypes();
                boolean acceptsToolExecution = compensatingParams.length == 1
                        && compensatingParams[0] == ToolExecution.class;
                if (!acceptsToolExecution
                        && !Arrays.equals(toolMethod.getParameterTypes(), compensatingParams)) {
                    compensatingToolMisconfiguration = illegalConfiguration(
                            "@CompensateFor(\"%s\") on method '%s.%s' must have the same parameter types as tool '%s'"
                                    + " or a single %s parameter",
                            toolName, objectWithTools.getClass().getName(), method.getName(),
                            toolName, ToolExecution.class.getSimpleName());
                    if (compensateOnToolErrors) {
                        throw compensatingToolMisconfiguration;
                    }
                    break;
                }
                if (acceptsToolExecution) {
                    method.setAccessible(true);
                    Method compensatingMethod = method;
                    compensatingActions.put(toolName, (toolExecution, ctx) -> {
                        try {
                            return toVoidFuture(compensatingMethod.invoke(objectWithTools, toolExecution));
                        } catch (Exception e) {
                            return CompletableFuture.failedFuture(e);
                        }
                    });
                } else {
                    DefaultToolExecutor executor = DefaultToolExecutor.builder()
                            .object(objectWithTools)
                            .originalMethod(toolMethod)
                            .methodToInvoke(method)
                            .propagateToolExecutionExceptions(true)
                            .build();
                    compensatingActions.put(toolName, (toolExecution, ctx) -> executor.executeAsync(
                                    toolExecution.request(), ctx)
                            .thenApply(result -> (Void) null));
                }
            }
        }
        return compensatingActions;
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

    /**
     * Resolves the {@link Executor} that the asynchronous AI Service modes run tools on: the one set via
     * {@link #executeToolsConcurrently(Executor)} / {@link #executeToolsConcurrently()}, or the default
     * virtual-thread executor when none was set. Never {@code null}, so async tools are always offloaded and
     * never block the model-response thread. Pass a single-threaded executor to run them serially.
     *
     * @since 1.19.0
     */
    public Executor effectiveToolExecutor() {
        return getOrDefault(executor, ToolService::defaultExecutor);
    }

    private static Executor defaultExecutor() {
        return DefaultExecutorProvider.getDefaultExecutor();
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
     * @since 1.17.0
     */
    public Consumer<BeforeToolExecution> beforeToolExecution() {
        return beforeToolExecution;
    }

    /**
     * @since 1.11.0
     */
    public void afterToolExecution(Consumer<ToolExecution> afterToolExecution) {
        this.afterToolExecution = afterToolExecution;
    }

    /**
     * @since 1.17.0
     */
    public Consumer<ToolExecution> afterToolExecution() {
        return afterToolExecution;
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
     * The {@link ToolArgumentsErrorHandler} for the asynchronous AI Service modes ({@code CompletableFuture},
     * reactive {@code Flow.Publisher}): a user-configured handler if present, otherwise the async default,
     * which sends the parsing error to the LLM so it can retry with corrected arguments.
     *
     * @since 1.19.0
     */
    public ToolArgumentsErrorHandler asyncArgumentsErrorHandler() {
        return getOrDefault(argumentsErrorHandler, DEFAULT_ASYNC_TOOL_ARGUMENTS_ERROR_HANDLER);
    }

    /**
     * The {@link ToolExecutionErrorHandler} for the asynchronous AI Service modes ({@code CompletableFuture},
     * reactive {@code Flow.Publisher}): a user-configured handler if present, otherwise the async default,
     * which fails the AI Service invocation (rather than hiding the error from the developer by sending it to
     * the LLM).
     *
     * @since 1.19.0
     */
    public ToolExecutionErrorHandler asyncExecutionErrorHandler() {
        return getOrDefault(executionErrorHandler, DEFAULT_ASYNC_TOOL_EXECUTION_ERROR_HANDLER);
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
        return executeInferenceAndToolsLoop(
                context,
                memoryId,
                chatResponse,
                parameters,
                messages,
                chatMemory,
                invocationContext,
                toolServiceContext,
                context.chatModel::chat);
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
            Function<ChatRequest, ChatResponse> chatModelInvoker) {
        TokenUsage aggregateTokenUsage = chatResponse.metadata().tokenUsage();
        List<ToolExecution> toolExecutions = new ArrayList<>();
        List<ChatResponse> intermediateResponses = new ArrayList<>();
        List<CompensableToolExecution> compensableExecutions = compensateOnToolErrors ? new ArrayList<>() : null;

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
                    invocationContext,
                    toolServiceContext);

            List<ToolExecutionResultMessage> resultMessages = outcome.resultMessages();

            compensateIfNeeded(
                    toolExecutionRequests,
                    toolResults,
                    resultMessages,
                    compensableExecutions,
                    outcome.anyToolErrored(),
                    chatMemory,
                    messages,
                    invocationContext,
                    context.eventListenerRegistrar);

            List<ChatMessage> nextMessages = persistToolResultsAndResolveMessagesSync(
                    context, chatMemory, messages, resultMessages, invocationContext);

            if (shouldReturnImmediately(outcome.anyToolErrored(), outcome.returnBehaviors())) {
                return immediateToolServiceResult(intermediateResponses, toolExecutions, aggregateTokenUsage);
            }

            NextChatRequest next = prepareNextChatRequest(
                    context,
                    memoryId,
                    nextMessages,
                    invocationContext,
                    toolServiceContext,
                    toolResults,
                    parameters);
            messages = next.messages();
            toolServiceContext = next.toolServiceContext();
            parameters = next.parameters();

            chatResponse = chatModelInvoker.apply(next.chatRequest());
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
     * <p>
     * The {@code cancellation} future lets the caller stop the loop: once it is cancelled, no further model
     * call or tool execution is initiated, and any in-flight model call is cancelled. A tool execution that
     * has <b>already started</b> is <b>not</b> interrupted — it runs to completion and its result is simply
     * discarded (Java cannot safely interrupt arbitrary tool code; this is a deliberate best-effort contract).
     *
     * @since 1.19.0
     */
    public CompletableFuture<ToolServiceResult> executeInferenceAndToolsLoopAsync(
            AiServiceContext context,
            Object memoryId,
            ChatResponse chatResponse,
            ChatRequestParameters parameters,
            List<ChatMessage> messages,
            ChatMemory chatMemory,
            InvocationContext invocationContext,
            ToolServiceContext toolServiceContext,
            CompletableFuture<?> cancellation) {
        return executeInferenceAndToolsLoopAsync(
                context,
                memoryId,
                chatResponse,
                parameters,
                messages,
                chatMemory,
                invocationContext,
                toolServiceContext,
                cancellation,
                context.chatModel::chatAsync);
    }

    /**
     * As {@link #executeInferenceAndToolsLoopAsync(AiServiceContext, Object, ChatResponse, ChatRequestParameters,
     * List, ChatMemory, InvocationContext, ToolServiceContext, CompletableFuture)}, but with a caller-supplied
     * asynchronous model invoker. Lets a streaming-only AI Service drive the same loop by bridging its
     * {@code StreamingChatModel} to a {@code CompletableFuture<ChatResponse>}, instead of requiring a
     * {@code ChatModel}.
     *
     * @since 1.19.0
     */
    public CompletableFuture<ToolServiceResult> executeInferenceAndToolsLoopAsync(
            AiServiceContext context,
            Object memoryId,
            ChatResponse chatResponse,
            ChatRequestParameters parameters,
            List<ChatMessage> messages,
            ChatMemory chatMemory,
            InvocationContext invocationContext,
            ToolServiceContext toolServiceContext,
            CompletableFuture<?> cancellation,
            Function<ChatRequest, CompletableFuture<ChatResponse>> chatModelInvoker) {
        AsyncToolLoop loop =
                new AsyncToolLoop(context, memoryId, chatMemory, invocationContext, cancellation, chatModelInvoker);
        return loop.withCancellationCompensation(loop.run(
                chatResponse,
                parameters,
                messages,
                toolServiceContext,
                chatResponse.metadata().tokenUsage(),
                maxToolCallingRoundTrips));
    }

    /**
     * Drives the non-blocking tool-calling loop for the {@code CompletableFuture} and reactive AI Service modes.
     * <p>
     * The invariants that never change across tool-calling rounds (the model invoker, memory, context, cancellation)
     * and the accumulators shared across rounds (tool executions, intermediate responses, compensable executions) are
     * held as fields, so each step takes only the per-round state as arguments. This keeps the loop a linear sequence
     * of composed steps ({@link #run} -> {@link #afterToolsExecuted} -> {@link #callModelAndContinue} -> {@link #run})
     * instead of a single deeply nested lambda.
     */
    private final class AsyncToolLoop {

        private final AiServiceContext context;
        private final Object memoryId;
        private final ChatMemory chatMemory;
        private final InvocationContext invocationContext;
        private final CompletableFuture<?> cancellation;
        private final Function<ChatRequest, CompletableFuture<ChatResponse>> chatModelInvoker;

        private final List<ToolExecution> toolExecutions = new ArrayList<>();
        private final List<ChatResponse> intermediateResponses = new ArrayList<>();
        private final List<CompensableToolExecution> compensableExecutions = newCompensableExecutionsAccumulator();

        private final AtomicReference<CompletableFuture<ChatResponse>> inFlightModelCall = new AtomicReference<>();

        AsyncToolLoop(
                AiServiceContext context,
                Object memoryId,
                ChatMemory chatMemory,
                InvocationContext invocationContext,
                CompletableFuture<?> cancellation,
                Function<ChatRequest, CompletableFuture<ChatResponse>> chatModelInvoker) {
            this.context = context;
            this.memoryId = memoryId;
            this.chatMemory = chatMemory;
            this.invocationContext = invocationContext;
            this.cancellation = cancellation;
            this.chatModelInvoker = chatModelInvoker;
            if (cancellation != null) {
                cancellation.whenComplete((ignored, error) -> {
                    if (cancellation.isCancelled()) {
                        CompletableFuture<ChatResponse> current = inFlightModelCall.get();
                        if (current != null) {
                            current.cancel(true);
                        }
                    }
                });
            }
        }

        /**
         * Runs one round: adds the model's message to memory, and either finishes (no tool calls) or executes the
         * requested tools and hands off to {@link #afterToolsExecuted}. Recurses for the next round.
         */
        CompletableFuture<ToolServiceResult> run(
                ChatResponse chatResponse,
                ChatRequestParameters parameters,
                List<ChatMessage> messages,
                ToolServiceContext toolServiceContext,
                TokenUsage aggregateTokenUsage,
                int roundTripsLeft) {

            if (isCancelled(cancellation)) {
                return CompletableFuture.failedFuture(new CancellationException());
            }

            if (roundTripsLeft == 0) {
                return CompletableFuture.failedFuture(runtime(
                        "Something is wrong, exceeded %s tool calling round trips (maxToolCallingRoundTrips)",
                        maxToolCallingRoundTrips));
            }

            AiMessage aiMessage = chatResponse.aiMessage();

            final List<ChatMessage> accumulator;
            final CompletionStage<Void> aiMessageAdded;
            if (chatMemory != null) {
                aiMessageAdded = chatMemory.addAsync(List.of(aiMessage));
                accumulator = messages;
            } else {
                List<ChatMessage> updated = new ArrayList<>(messages);
                updated.add(aiMessage);
                accumulator = updated;
                aiMessageAdded = CompletableFuture.completedFuture(null);
            }

            return aiMessageAdded
                    .thenCompose(ignored -> {
                        if (!aiMessage.hasToolExecutionRequests()) {
                            return CompletableFuture.completedFuture(finalToolServiceResult(
                                    chatResponse, intermediateResponses, toolExecutions, aggregateTokenUsage));
                        }

                        intermediateResponses.add(chatResponse);

                        List<ToolExecutionRequest> toolExecutionRequests = aiMessage.toolExecutionRequests();
                        return executeToolsCollectingErrors(
                                        toolExecutionRequests, toolServiceContext.toolExecutors(), invocationContext)
                                .thenCompose(combined -> afterToolsExecuted(
                                        parameters,
                                        accumulator,
                                        toolServiceContext,
                                        aggregateTokenUsage,
                                        roundTripsLeft,
                                        toolExecutionRequests,
                                        combined.results(),
                                        combined.firstError()));
                    })
                    .toCompletableFuture();
        }

        /**
         * After the round's tools have executed: records their results, compensates if a tool errored, and either
         * returns immediately (per {@link ReturnBehavior}) or persists the results and continues via
         * {@link #callModelAndContinue}.
         */
        private CompletableFuture<ToolServiceResult> afterToolsExecuted(
                ChatRequestParameters parameters,
                List<ChatMessage> accumulator,
                ToolServiceContext toolServiceContext,
                TokenUsage aggregateTokenUsage,
                int roundTripsLeft,
                List<ToolExecutionRequest> toolExecutionRequests,
                Map<ToolExecutionRequest, ToolExecutionResult> toolResults,
                Throwable firstError) {

            if (isCancelled(cancellation)) {
                List<ToolExecutionResultMessage> resultMessages = toolExecutionRequests.stream()
                        .map(request -> toResultMessage(request, toolResults.get(request)))
                        .toList();
                synchronized (compensableExecutions) {
                    collectCompensable(
                            toolExecutionRequests, toolResults, resultMessages, compensableExecutions, invocationContext);
                }
                return CompletableFuture.failedFuture(
                        firstError instanceof CancellationException ? firstError : new CancellationException());
            }

            ToolResultsOutcome outcome = processToolResults(
                    context, toolExecutionRequests, toolResults, toolExecutions, invocationContext, toolServiceContext);

            return compensateIfNeededAsync(
                            toolExecutionRequests,
                            toolResults,
                            outcome.resultMessages(),
                            compensableExecutions,
                            outcome.anyToolErrored(),
                            chatMemory,
                            accumulator,
                            invocationContext,
                            CompensationReason.TOOL_EXECUTION_FAILED,
                            context.eventListenerRegistrar,
                            null)
                    .handle((ignored, compensationError) -> compensationError)
                    .thenCompose(compensationError -> {
                        if (firstError != null) {
                            if (compensationError != null) {
                                firstError.addSuppressed(unwrapCompletionException(compensationError));
                            }
                            return CompletableFuture.<ToolServiceResult>failedFuture(firstError);
                        }
                        if (compensationError != null) {
                            return CompletableFuture.<ToolServiceResult>failedFuture(
                                    unwrapCompletionException(compensationError));
                        }

                        if (shouldReturnImmediately(outcome.anyToolErrored(), outcome.returnBehaviors())) {
                            return CompletableFuture.completedFuture(immediateToolServiceResult(
                                    intermediateResponses, toolExecutions, aggregateTokenUsage));
                        }

                        if (isCancelled(cancellation)) {
                            return CompletableFuture.<ToolServiceResult>failedFuture(new CancellationException());
                        }

                        return persistToolResultsAndResolveMessages(
                                        context, chatMemory, accumulator, outcome.resultMessages(), invocationContext)
                                .thenCompose(nextMessages -> callModelAndContinue(
                                        parameters,
                                        toolServiceContext,
                                        aggregateTokenUsage,
                                        roundTripsLeft,
                                        toolResults,
                                        nextMessages))
                                .toCompletableFuture();
                    });
        }

        /**
         * Wraps the loop so that a cancellation rolls back every compensable tool that had already executed
         * successfully (across all rounds) before the {@link CancellationException} is surfaced - the "drain the
         * round, then roll back" contract. Individual rounds only accumulate their compensable tools; this single
         * handler runs their compensating actions, whether the cancellation arrived mid-round or between rounds.
         */
        CompletableFuture<ToolServiceResult> withCancellationCompensation(CompletableFuture<ToolServiceResult> result) {
            return result.exceptionallyCompose(error -> {
                Throwable cause = unwrapCompletionException(error);
                if (compensateOnToolErrors
                        && cause instanceof CancellationException
                        && compensableExecutions != null
                        && !compensableExecutions.isEmpty()) {
                    return compensateOnCancellationAsync(
                                    null,
                                    null,
                                    compensableExecutions,
                                    chatMemory,
                                    null,
                                    invocationContext,
                                    context.eventListenerRegistrar,
                                    null)
                            .thenCompose(ignored -> CompletableFuture.<ToolServiceResult>failedFuture(cause));
                }
                return CompletableFuture.failedFuture(error);
            });
        }

        /**
         * Builds the next chat request, re-invokes the model without blocking, and recurses into {@link #run} for the
         * next round.
         */
        private CompletableFuture<ToolServiceResult> callModelAndContinue(
                ChatRequestParameters parameters,
                ToolServiceContext toolServiceContext,
                TokenUsage aggregateTokenUsage,
                int roundTripsLeft,
                Map<ToolExecutionRequest, ToolExecutionResult> toolResults,
                List<ChatMessage> nextMessages) {

            NextChatRequest next = prepareNextChatRequest(
                    context, memoryId, nextMessages, invocationContext, toolServiceContext, toolResults, parameters);

            CompletableFuture<ChatResponse> nextModelCall = chatModelInvoker.apply(next.chatRequest());
            inFlightModelCall.set(nextModelCall);
            if (isCancelled(cancellation)) {
                nextModelCall.cancel(true);
            }
            return nextModelCall.thenCompose(nextChatResponse -> {
                fireResponseReceivedEvent(
                        next.chatRequest(), nextChatResponse, invocationContext, context.eventListenerRegistrar);
                return run(
                        nextChatResponse,
                        next.parameters(),
                        next.messages(),
                        next.toolServiceContext(),
                        TokenUsage.sum(aggregateTokenUsage, nextChatResponse.metadata().tokenUsage()),
                        roundTripsLeft - 1);
            });
        }
    }

    private static boolean isCancelled(CompletableFuture<?> cancellation) {
        return cancellation != null && cancellation.isCancelled();
    }


    /**
     * Per-round bookkeeping shared by every AI Service mode (sync, {@code CompletableFuture}, {@code TokenStream},
     * reactive {@code Flow.Publisher}): for each executed tool it records a {@link ToolExecution}, fires the
     * {@link ToolExecutedEvent}, collects the tool-result message (in request order), and accumulates
     * {@code anyToolErrored} + the per-tool {@link ReturnBehavior}s. This bookkeeping is intentionally free of
     * memory I/O — persisting the collected result messages (synchronously or composed) is left to each mode via
     * {@link #persistToolResultsAndResolveMessages} / {@link #persistToolResultsAndResolveMessagesSync}, so memory
     * work happens on the appropriate thread. The tool <i>execution</i> itself and the delivery of intermediate
     * responses are likewise left to each mode.
     *
     * @since 1.19.0
     */
    public ToolResultsOutcome processToolResults(
            AiServiceContext context,
            List<ToolExecutionRequest> toolExecutionRequests,
            Map<ToolExecutionRequest, ToolExecutionResult> toolResults,
            List<ToolExecution> toolExecutions,
            InvocationContext invocationContext,
            ToolServiceContext toolServiceContext) {

        boolean anyToolErrored = false;
        List<ReturnBehavior> returnBehaviors = new ArrayList<>(toolExecutionRequests.size());
        List<ToolExecutionResultMessage> resultMessages = new ArrayList<>(toolExecutionRequests.size());

        for (ToolExecutionRequest request : toolExecutionRequests) {
            ToolExecutionResult result = toolResults.get(request);
            resultMessages.add(toResultMessage(request, result));

            ToolExecution toolExecution = ToolExecution.builder()
                    .request(request)
                    .result(result)
                    .invocationContext(invocationContext)
                    .build();
            toolExecutions.add(toolExecution);

            fireToolExecutedEvent(invocationContext, request, toolExecution, context.eventListenerRegistrar);

            anyToolErrored = anyToolErrored || result.isError();
            returnBehaviors.add(toolServiceContext.returnBehavior(request.name()));
        }

        return new ToolResultsOutcome(anyToolErrored, returnBehaviors, resultMessages);
    }

    /**
     * Persists the tool-result messages and resolves the messages to send in the next request — the memory-touching
     * counterpart to {@link #processToolResults}, kept out of that shared bookkeeping so that each AI Service mode
     * can perform the memory I/O on the appropriate thread (synchronously for the sync/{@code TokenStream} modes,
     * composed for the {@code CompletableFuture}/reactive modes).
     * <p>
     * When {@code chatMemory} is {@code null}, the result messages are appended to {@code accumulator} and that
     * list is returned. When present, each result message is added to memory (sequentially, never concurrently, so
     * the underlying read-modify-write stays ordered) and the resolved memory view is returned.
     *
     * @since 1.19.0
     */
    public CompletionStage<List<ChatMessage>> persistToolResultsAndResolveMessages(
            AiServiceContext context,
            ChatMemory chatMemory,
            List<ChatMessage> accumulator,
            List<ToolExecutionResultMessage> resultMessages,
            InvocationContext invocationContext) {

        if (chatMemory == null) {
            accumulator.addAll(resultMessages);
            return CompletableFuture.completedFuture(accumulator);
        }

        List<ChatMessage> messagesToAdd = new ArrayList<>(resultMessages);
        return chatMemory
                .addAsync(messagesToAdd)
                .thenCompose(ignored -> chatMemory.messagesAsync())
                .thenApply(memoryMessages -> resolveMessagesForNextRequest(memoryMessages, context, invocationContext))
                .exceptionallyCompose(ToolService::translateBlockingChatMemory);
    }

    private static CompletionStage<List<ChatMessage>> translateBlockingChatMemory(Throwable error) {
        Throwable cause = unwrapCompletionException(error);
        if (cause instanceof AsyncNotSupportedException) {
            return CompletableFuture.failedFuture(new UnsupportedFeatureException(cause.getMessage()
                    + " The asynchronous/reactive AI Service requires the chat memory's ChatMemoryStore to implement"
                    + " its async methods (getMessagesAsync/updateMessagesAsync/deleteMessagesAsync)."));
        }
        return CompletableFuture.failedFuture(error);
    }

    /**
     * Blocking counterpart of {@link #persistToolResultsAndResolveMessages} for the synchronous and
     * {@code TokenStream} AI Service modes, which use the synchronous {@link ChatMemory} methods (so a memory
     * backed by a blocking store that only implements the synchronous methods keeps working).
     *
     * @since 1.19.0
     */
    public List<ChatMessage> persistToolResultsAndResolveMessagesSync(
            AiServiceContext context,
            ChatMemory chatMemory,
            List<ChatMessage> accumulator,
            List<ToolExecutionResultMessage> resultMessages,
            InvocationContext invocationContext) {

        if (chatMemory == null) {
            accumulator.addAll(resultMessages);
            return accumulator;
        }

        resultMessages.forEach(chatMemory::add);
        return resolveMessagesForNextRequest(chatMemory.messages(), context, invocationContext);
    }

    /**
     * Resolves the messages to send in the next request from the current memory view, honoring
     * {@code storeRetrievedContentInChatMemory} (when {@code false}, the last user message is replaced with the
     * original, un-augmented one so retrieved content is not persisted across rounds).
     *
     * @since 1.19.0
     */
    public static List<ChatMessage> resolveMessagesForNextRequest(
            List<ChatMessage> memoryMessages, AiServiceContext context, InvocationContext invocationContext) {
        if (context.storeRetrievedContentInChatMemory) {
            return memoryMessages;
        }
        return UserMessage.replaceLast(memoryMessages, invocationContext.userMessage());
    }

    /**
     * Builds the next round's {@link ChatRequest}, shared by every AI Service mode: given the already-resolved
     * {@code messages} to send (see {@link #resolveMessagesForNextRequest}), it refreshes dynamic tool providers,
     * folds in any tools found by tool search, overrides the tool specifications on the carried-forward
     * {@code parameters}, applies the chat-request transformer, and fires the {@link AiServiceRequestIssuedEvent}.
     * Reading the messages from memory is left to each mode (synchronously or composed) so this method performs no
     * memory I/O. Each mode supplies how the returned request is actually dispatched.
     *
     * @since 1.19.0
     */
    public NextChatRequest prepareNextChatRequest(
            AiServiceContext context,
            Object memoryId,
            List<ChatMessage> messages,
            InvocationContext invocationContext,
            ToolServiceContext toolServiceContext,
            Map<ToolExecutionRequest, ToolExecutionResult> toolResults,
            ChatRequestParameters parameters) {

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

    public record ToolResultsOutcome(
            boolean anyToolErrored,
            List<ReturnBehavior> returnBehaviors,
            List<ToolExecutionResultMessage> resultMessages) {}

    public record NextChatRequest(
            ChatRequest chatRequest,
            List<ChatMessage> messages,
            ToolServiceContext toolServiceContext,
            ChatRequestParameters parameters) {}

    /**
     * Synchronous tool compensation, used by the synchronous / {@code TokenStream} modes. Collects this round's
     * successful compensable tools (accumulated across rounds) and, if any tool in this round errored, rolls them
     * all back — running their compensating actions, rewriting the chat memory (synchronously), and rewriting this
     * round's {@code resultMessages} in place — before the result messages are persisted. A compensating action
     * that returns a {@link CompletableFuture} is awaited. Returns immediately when compensation is disabled.
     *
     * @since 1.19.0
     */
    public void compensateIfNeeded(
            List<ToolExecutionRequest> toolExecutionRequests,
            Map<ToolExecutionRequest, ToolExecutionResult> toolResults,
            List<ToolExecutionResultMessage> resultMessages,
            List<CompensableToolExecution> compensableExecutions,
            boolean anyToolErrored,
            ChatMemory chatMemory,
            List<ChatMessage> messages,
            InvocationContext invocationContext,
            AiServiceListenerRegistrar listenerRegistrar) {

        if (!compensateOnToolErrors) {
            return;
        }
        String failedToolName =
                collectCompensable(toolExecutionRequests, toolResults, resultMessages, compensableExecutions, invocationContext);
        if (anyToolErrored && !compensableExecutions.isEmpty()) {
            List<CompensableToolExecution> compensated = List.copyOf(compensableExecutions);
            compensateToolsActions(compensableExecutions, invocationContext).join();
            rewriteChatMemoryForCompensatedTools(
                    messages, chatMemory, compensableExecutions, CompensationReason.TOOL_EXECUTION_FAILED, failedToolName);
            compensableExecutions.clear();
            rewriteCurrentResults(
                    toolExecutionRequests, toolResults, resultMessages, CompensationReason.TOOL_EXECUTION_FAILED, failedToolName);
            fireCompensatedEvents(
                    compensated, CompensationReason.TOOL_EXECUTION_FAILED, invocationContext, listenerRegistrar, null);
        }
    }

    /**
     * Non-blocking counterpart of {@link #compensateIfNeeded}, used by the {@code CompletableFuture} and reactive
     * modes. The compensating actions and the chat-memory rewrite run without blocking the model-delivery thread: a
     * compensating action that performs blocking I/O should return a {@link CompletableFuture}, and the rewrite uses
     * {@link ChatMemory#setAsync(List)}.
     *
     * @since 1.19.0
     */
    public CompletableFuture<Void> compensateIfNeededAsync(
            List<ToolExecutionRequest> toolExecutionRequests,
            Map<ToolExecutionRequest, ToolExecutionResult> toolResults,
            List<ToolExecutionResultMessage> resultMessages,
            List<CompensableToolExecution> compensableExecutions,
            boolean anyToolErrored,
            ChatMemory chatMemory,
            List<ChatMessage> messages,
            InvocationContext invocationContext,
            CompensationReason reason,
            AiServiceListenerRegistrar listenerRegistrar,
            BiConsumer<ToolExecution, CompensationReason> streamEmitter) {
        return compensateIfNeededAsync(
                toolExecutionRequests,
                toolResults,
                resultMessages,
                compensableExecutions,
                anyToolErrored,
                chatMemory,
                messages,
                invocationContext,
                reason,
                listenerRegistrar,
                streamEmitter,
                false,
                null);
    }

    /**
     * As {@link #compensateIfNeededAsync}, but when {@code alreadyCollected} is {@code true} this round's compensable
     * tools have already been recorded into {@code compensableExecutions} (via {@link #collectCompensableRound}) and
     * {@code preCollectedFailedToolName} carries the collect's result; the collection step is then skipped. The
     * reactive streaming path uses this so a round's compensable tools are collected <em>before</em> the inflight-round
     * handoff, closing the window where a concurrent cancellation could snapshot the accumulator before the collection.
     *
     * @since 1.19.0
     */
    public CompletableFuture<Void> compensateIfNeededAsync(
            List<ToolExecutionRequest> toolExecutionRequests,
            Map<ToolExecutionRequest, ToolExecutionResult> toolResults,
            List<ToolExecutionResultMessage> resultMessages,
            List<CompensableToolExecution> compensableExecutions,
            boolean anyToolErrored,
            ChatMemory chatMemory,
            List<ChatMessage> messages,
            InvocationContext invocationContext,
            CompensationReason reason,
            AiServiceListenerRegistrar listenerRegistrar,
            BiConsumer<ToolExecution, CompensationReason> streamEmitter,
            boolean alreadyCollected,
            String preCollectedFailedToolName) {

        if (!compensateOnToolErrors) {
            return CompletableFuture.completedFuture(null);
        }
        List<CompensableToolExecution> toCompensate;
        String failedToolName;
        synchronized (compensableExecutions) {
            failedToolName = alreadyCollected
                    ? preCollectedFailedToolName
                    : collectCompensable(
                            toolExecutionRequests, toolResults, resultMessages, compensableExecutions, invocationContext);
            boolean triggered = reason == CompensationReason.INVOCATION_CANCELLED || anyToolErrored;
            if (!triggered || compensableExecutions.isEmpty()) {
                return CompletableFuture.completedFuture(null);
            }
            toCompensate = List.copyOf(compensableExecutions);
            compensableExecutions.clear();
        }
        rewriteCurrentResults(toolExecutionRequests, toolResults, resultMessages, reason, failedToolName);
        String rolledBackByToolName = failedToolName;
        return compensateToolsActions(toCompensate, invocationContext)
                .thenCompose(ignored -> rewriteChatMemoryForCompensatedToolsAsync(
                        messages, chatMemory, toCompensate, reason, rolledBackByToolName))
                .thenRun(() -> fireCompensatedEvents(toCompensate, reason, invocationContext, listenerRegistrar, streamEmitter));
    }

    /**
     * Fires a {@link ToolCompensatedEvent} to the observability listeners for each rolled-back tool, and (when a
     * {@code streamEmitter} is provided, i.e. on the reactive streaming path) relays the same into the event stream.
     */
    private void fireCompensatedEvents(
            List<CompensableToolExecution> compensated,
            CompensationReason reason,
            InvocationContext invocationContext,
            AiServiceListenerRegistrar listenerRegistrar,
            BiConsumer<ToolExecution, CompensationReason> streamEmitter) {
        for (CompensableToolExecution compensable : compensated) {
            ToolExecution toolExecution = compensable.toolExecution();
            if (listenerRegistrar != null) {
                listenerRegistrar.fireEvent(ToolCompensatedEvent.builder()
                        .invocationContext(invocationContext)
                        .request(toolExecution.request())
                        .resultContents(toolExecution.resultContents())
                        .reason(reason)
                        .build());
            }
            if (streamEmitter != null) {
                streamEmitter.accept(toolExecution, reason);
            }
        }
    }

    /**
     * Rolls back, on cancellation, every compensable tool that had already executed successfully - across all
     * rounds - implementing the "drain the round, then roll back" contract for the asynchronous and reactive modes.
     * If the current (drained) round's requests/results are supplied, its successful compensable tools are recorded
     * first so they roll back together with those accumulated from earlier rounds. Runs the compensating actions,
     * rewrites the chat memory, clears the accumulator, and fires a {@link ToolCompensatedEvent} per rolled-back tool
     * (reason {@link CompensationReason#INVOCATION_CANCELLED}). A no-op when compensation is disabled or nothing is
     * pending.
     *
     * @since 1.19.0
     */
    public CompletableFuture<Void> compensateOnCancellationAsync(
            List<ToolExecutionRequest> currentRoundRequests,
            Map<ToolExecutionRequest, ToolExecutionResult> currentRoundResults,
            List<CompensableToolExecution> compensableExecutions,
            ChatMemory chatMemory,
            List<ChatMessage> messages,
            InvocationContext invocationContext,
            AiServiceListenerRegistrar listenerRegistrar,
            BiConsumer<ToolExecution, CompensationReason> streamEmitter) {

        if (!compensateOnToolErrors || compensableExecutions == null) {
            return CompletableFuture.completedFuture(null);
        }
        List<CompensableToolExecution> toCompensate;
        // Same accumulator serialization as compensateIfNeededAsync (see its note): collect the drained round and
        // snapshot+clear atomically so the async rollback runs on an immutable copy and each tool rolls back once.
        synchronized (compensableExecutions) {
            if (currentRoundRequests != null && currentRoundResults != null) {
                List<ToolExecutionResultMessage> resultMessages = currentRoundRequests.stream()
                        .map(request -> toResultMessage(request, currentRoundResults.get(request)))
                        .toList();
                collectCompensable(
                        currentRoundRequests, currentRoundResults, resultMessages, compensableExecutions, invocationContext);
            }
            if (compensableExecutions.isEmpty()) {
                return CompletableFuture.completedFuture(null);
            }
            toCompensate = List.copyOf(compensableExecutions);
            compensableExecutions.clear();
        }
        return compensateToolsActions(toCompensate, invocationContext)
                .thenCompose(ignored -> (chatMemory != null || messages != null)
                        ? rewriteChatMemoryForCompensatedToolsAsync(
                                messages, chatMemory, toCompensate, CompensationReason.INVOCATION_CANCELLED, null)
                        : CompletableFuture.completedFuture(null))
                .thenRun(() -> fireCompensatedEvents(
                        toCompensate,
                        CompensationReason.INVOCATION_CANCELLED,
                        invocationContext,
                        listenerRegistrar,
                        streamEmitter));
    }

    /**
     * Collects this round's successful compensable tool executions into {@code compensableExecutions} (which
     * accumulates across rounds), and returns the name of the first tool in this round that errored (or null).
     */
    /**
     * Records one completed round's successful compensable tool executions into the shared {@code accumulator} and
     * returns the first errored tool's name (or {@code null}). The caller MUST hold the monitor of {@code accumulator}
     * across this call together with whatever "round is complete" handoff it performs (e.g. clearing the inflight
     * round), so that a concurrent cancellation - which reads that handoff and snapshots the accumulator under the same
     * monitor - observes this round's tools as present once it sees the round released. Used by the reactive streaming
     * path; the result messages are derived the same way as the cancellation path ({@link #toResultMessage}).
     *
     * @since 1.19.0
     */
    public String collectCompensableRound(
            List<ToolExecutionRequest> toolExecutionRequests,
            Map<ToolExecutionRequest, ToolExecutionResult> toolResults,
            List<CompensableToolExecution> compensableExecutions,
            InvocationContext invocationContext) {
        List<ToolExecutionResultMessage> resultMessages = toolExecutionRequests.stream()
                .map(request -> toResultMessage(request, toolResults.get(request)))
                .toList();
        return collectCompensable(
                toolExecutionRequests, toolResults, resultMessages, compensableExecutions, invocationContext);
    }

    private String collectCompensable(
            List<ToolExecutionRequest> toolExecutionRequests,
            Map<ToolExecutionRequest, ToolExecutionResult> toolResults,
            List<ToolExecutionResultMessage> resultMessages,
            List<CompensableToolExecution> compensableExecutions,
            InvocationContext invocationContext) {

        String failedToolName = null;
        for (int i = 0; i < toolExecutionRequests.size(); i++) {
            ToolExecutionRequest request = toolExecutionRequests.get(i);
            ToolExecutionResult result = toolResults.get(request);
            if (!result.isError() && compensatingExecutors.containsKey(request.name())) {
                ToolExecution toolExecution = ToolExecution.builder()
                        .request(request)
                        .result(result)
                        .invocationContext(invocationContext)
                        .build();
                compensableExecutions.add(new CompensableToolExecution(toolExecution, resultMessages.get(i)));
            }
            if (result.isError() && failedToolName == null) {
                failedToolName = request.name();
            }
        }
        return failedToolName;
    }

    private static CompletableFuture<Void> toVoidFuture(Object result) {
        if (result instanceof CompletionStage<?> stage) {
            return stage.thenApply(ignored -> (Void) null).toCompletableFuture();
        }
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Creates the accumulator the {@code compensableExecutions} are collected into across tool-calling rounds, or
     * {@code null} when compensation is disabled.
     *
     * @since 1.19.0
     */
    public List<CompensableToolExecution> newCompensableExecutionsAccumulator() {
        return compensateOnToolErrors ? new ArrayList<>() : null;
    }

    private void rewriteCurrentResults(List<ToolExecutionRequest> toolExecutionRequests,
                                       Map<ToolExecutionRequest, ToolExecutionResult> toolResults,
                                       List<ToolExecutionResultMessage> resultMessages,
                                       CompensationReason reason,
                                       String failedToolName) {
        for (int i = 0; i < toolExecutionRequests.size(); i++) {
            ToolExecutionRequest request = toolExecutionRequests.get(i);
            if (!toolResults.get(request).isError()
                    && compensatingExecutors.containsKey(request.name())) {
                resultMessages.set(i, rolledBackResultMessage(resultMessages.get(i), reason, failedToolName));
            }
        }
    }

    private static void rewriteChatMemoryForCompensatedTools(List<ChatMessage> messages,
                                                             ChatMemory chatMemory,
                                                             List<CompensableToolExecution> compensableExecutions,
                                                             CompensationReason reason,
                                                             String failedToolName) {
        List<ChatMessage> memoryMessages = chatMemory != null ? new ArrayList<>(chatMemory.messages()) : messages;
        replaceCompensatedMessages(memoryMessages, compensableExecutions, reason, failedToolName);
        if (chatMemory != null) {
            chatMemory.set(memoryMessages);
        }
    }

    private static CompletableFuture<Void> rewriteChatMemoryForCompensatedToolsAsync(
            List<ChatMessage> messages,
            ChatMemory chatMemory,
            List<CompensableToolExecution> compensableExecutions,
            CompensationReason reason,
            String failedToolName) {
        if (chatMemory == null) {
            replaceCompensatedMessages(messages, compensableExecutions, reason, failedToolName);
            return CompletableFuture.completedFuture(null);
        }
        return chatMemory.messagesAsync().thenCompose(memoryMessages -> {
            List<ChatMessage> rewritten = new ArrayList<>(memoryMessages);
            replaceCompensatedMessages(rewritten, compensableExecutions, reason, failedToolName);
            return chatMemory.setAsync(rewritten);
        });
    }

    private static void replaceCompensatedMessages(
            List<ChatMessage> memoryMessages,
            List<CompensableToolExecution> compensableExecutions,
            CompensationReason reason,
            String failedToolName) {
        for (CompensableToolExecution entry : compensableExecutions) {
            ToolExecutionResultMessage originalMsg = entry.resultMessage();
            ToolExecutionResultMessage replacementMsg = rolledBackResultMessage(originalMsg, reason, failedToolName);
            for (int j = 0; j < memoryMessages.size(); j++) {
                if (memoryMessages.get(j) instanceof ToolExecutionResultMessage msg
                        && msg.id().equals(originalMsg.id())) {
                    memoryMessages.set(j, replacementMsg);
                    break;
                }
            }
        }
    }

    private static ToolExecutionResultMessage rolledBackResultMessage(
            ToolExecutionResultMessage original, CompensationReason reason, String failedToolName) {
        String cause = reason == CompensationReason.INVOCATION_CANCELLED
                ? "the invocation was cancelled"
                : "failure of tool '" + failedToolName + "'";
        String rolledBackText = "Tool '" + original.toolName() + "' was executed successfully"
                + " but was rolled back due to " + cause;
        return original.toBuilder()
                .contents(List.of(TextContent.from(rolledBackText)))
                .isError(true)
                .build();
    }

    // Public only so the streaming publisher (a different package) can hold the compensable-execution accumulator
    // and pass it back to compensateOnCancellationAsync(...); not part of the user-facing API.
    @Internal
    public record CompensableToolExecution(ToolExecution toolExecution, ToolExecutionResultMessage resultMessage) {}

    /**
     * Runs the compensating actions in reverse order, composed into a single future. Each action runs after the
     * previous one completes (compensation order matters); a failing action is logged and does not abort the rest,
     * mirroring the previous synchronous behavior. A {@code void} compensating method completes immediately; a
     * {@code CompletableFuture}-returning one runs without blocking. The synchronous mode awaits the result.
     */
    private CompletableFuture<Void> compensateToolsActions(
            List<CompensableToolExecution> compensableExecutions, InvocationContext invocationContext) {
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (int i = compensableExecutions.size() - 1; i >= 0; i--) {
            ToolExecution toolExecution = compensableExecutions.get(i).toolExecution();
            String toolName = toolExecution.request().name();
            BiFunction<ToolExecution, InvocationContext, CompletableFuture<Void>> compensatingAction =
                    compensatingExecutors.get(toolName);
            chain = chain.thenCompose(ignored -> {
                CompletableFuture<Void> action;
                try {
                    action = compensatingAction.apply(toolExecution, invocationContext);
                } catch (Exception e) {
                    action = CompletableFuture.failedFuture(e);
                }
                return action.exceptionally(error -> {
                    log.warn("Compensating action failed for tool '{}': {}", toolName, error.getMessage(), error);
                    return null;
                });
            });
        }
        return chain;
    }

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
     * Non-blocking counterpart of {@link #execute(List, Map, InvocationContext)} that collects the results of all
     * tools (including failed ones) instead of short-circuiting on the first failure, so the caller can compensate
     * the successful tools before failing the invocation with the tool error. Every tool is started on the
     * {@linkplain #effectiveToolExecutor() effective executor} so the model-delivery thread is never blocked.
     */
    private CompletableFuture<CombinedToolResults> executeToolsCollectingErrors(
            List<ToolExecutionRequest> toolRequests,
            Map<String, ToolExecutor> toolExecutors,
            InvocationContext invocationContext) {
        Map<ToolExecutionRequest, CompletableFuture<ToolExecutionResult>> futures = new LinkedHashMap<>();
        for (ToolExecutionRequest toolRequest : toolRequests) {
            futures.put(
                    toolRequest,
                    startTool(toolRequest, toolExecutors, invocationContext, null, null, effectiveToolExecutor()));
        }
        return combineToolResultsCollectingErrors(futures);
    }

    /**
     * Initiates a single tool execution on the given {@code executor}, returning a future of its result. Used
     * by the non-blocking streaming AI Service to start a tool as soon as its {@code CompleteToolCall} arrives
     * (rather than waiting for the whole model response), so that concurrent tools overlap each other and the
     * tail of the model stream.
     *
     * @since 1.19.0
     */
    public CompletableFuture<ToolExecutionResult> startTool(
            ToolExecutionRequest toolRequest,
            Map<String, ToolExecutor> toolExecutors,
            InvocationContext invocationContext,
            Consumer<BeforeToolExecution> externalBeforeToolExecution,
            Consumer<ToolExecution> externalAfterToolExecution,
            Executor executor) {
        return CompletableFuture.supplyAsync(
                        () -> executeToolAsync(
                                invocationContext,
                                toolExecutors,
                                toolRequest,
                                externalBeforeToolExecution,
                                externalAfterToolExecution),
                        executor)
                .thenCompose(toolResultFuture -> toolResultFuture);
    }

    /**
     * The results of a round of tool executions, keeping successful and failed tools together so the caller can
     * compensate the tools that <b>did</b> succeed before failing the invocation with the tool error.
     *
     * @param results    every tool's result in request order; a tool that threw is represented by an
     *                   {@linkplain ToolExecutionResult#isError() error} result carrying its error text, so
     *                   downstream bookkeeping and compensation see a complete, ordered map
     * @param firstError the first tool failure in request order, or {@code null} if every tool succeeded
     * @since 1.19.0
     */
    public record CombinedToolResults(
            Map<ToolExecutionRequest, ToolExecutionResult> results, Throwable firstError) {}

    /**
     * Combines a set of in-flight (possibly already-started) tool executions into a single future of their results,
     * keyed and ordered by request. Unlike a short-circuiting combine, it does <b>not</b> stop on the first tool
     * failure: it waits for
     * all tool executions to settle and returns every result (a failed tool becomes an error result), together with
     * the first failure in request order. This lets the asynchronous AI Service modes, whose default
     * {@link ToolExecutionErrorHandler} rethrows execution errors, still run the compensating actions of the tools
     * that succeeded before failing the invocation with the tool error. The given map's iteration order determines
     * the result order, so pass a {@link LinkedHashMap} in request order.
     *
     * @since 1.19.0
     */
    public static CompletableFuture<CombinedToolResults> combineToolResultsCollectingErrors(
            Map<ToolExecutionRequest, CompletableFuture<ToolExecutionResult>> futures) {
        return CompletableFuture.allOf(futures.values().toArray(new CompletableFuture[0]))
                // wait for ALL futures to complete (normally or not), then collect below in request order
                .handle((ignored, ignoredError) -> ignored)
                .thenApply(ignored -> {
                    Map<ToolExecutionRequest, ToolExecutionResult> results = new LinkedHashMap<>();
                    Throwable firstError = null;
                    for (Map.Entry<ToolExecutionRequest, CompletableFuture<ToolExecutionResult>> entry :
                            futures.entrySet()) {
                        CompletableFuture<ToolExecutionResult> future = entry.getValue();
                        if (future.isCompletedExceptionally()) {
                            Throwable error = extractError(future);
                            if (firstError == null) {
                                firstError = error;
                            }
                            results.put(
                                    entry.getKey(),
                                    ToolExecutionResult.builder()
                                            .isError(true)
                                            .resultText(errorText(error))
                                            .build());
                        } else {
                            // getNow never blocks: all futures have already completed at this point
                            results.put(entry.getKey(), future.getNow(null));
                        }
                    }
                    return new CombinedToolResults(results, firstError);
                });
    }

    /**
     * Extracts the failure of an already-exceptionally-completed future without blocking, unwrapping the
     * {@link java.util.concurrent.CompletionException} that {@link CompletableFuture#getNow(Object)} wraps it in.
     */
    private static Throwable extractError(CompletableFuture<?> future) {
        try {
            future.getNow(null);
            return null; // unreachable: the future is completed exceptionally
        } catch (CancellationException e) {
            return e;
        } catch (Throwable e) {
            return unwrapCompletionException(e);
        }
    }

    private CompletableFuture<ToolExecutionResult> executeToolAsync(
            InvocationContext invocationContext,
            Map<String, ToolExecutor> toolExecutors,
            ToolExecutionRequest toolRequest,
            Consumer<BeforeToolExecution> externalBeforeToolExecution,
            Consumer<ToolExecution> externalAfterToolExecution) {
        return internalExecuteToolAsync(
                invocationContext,
                toolExecutors,
                toolRequest,
                nullSafeCombineConsumers(this.beforeToolExecution, externalBeforeToolExecution),
                nullSafeCombineConsumers(this.afterToolExecution, externalAfterToolExecution));
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
                    toolRequest,
                    toolExecutor,
                    invocationContext,
                    asyncArgumentsErrorHandler(),
                    asyncExecutionErrorHandler());
        }

        if (afterToolExecution != null) {
            futureToolResult = futureToolResult.handle((toolResult, error) -> {
                // Emit the "after" notification for every terminated execution - success OR failure - so an
                // observer always sees a completed execution balancing the "before" one. A tool that threw (e.g.
                // under the default rethrow ToolExecutionErrorHandler) is reported with an error result
                // (hasFailed() == true); the future is kept failed so the invocation still aborts.
                ToolExecutionResult result = error == null
                        ? toolResult
                        : ToolExecutionResult.builder()
                                .isError(true)
                                .resultText(errorText(unwrapCompletionException(error)))
                                .build();
                afterToolExecution.accept(ToolExecution.builder()
                        .request(toolRequest)
                        .result(result)
                        .startTime(startTime)
                        .finishTime(LocalDateTime.now())
                        .invocationContext(invocationContext)
                        .build());
                if (error != null) {
                    throw error instanceof CompletionException ce ? ce : new CompletionException(error);
                }
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
        } catch (AsyncNotSupportedException e) {
            return CompletableFuture.failedFuture(e);
        } catch (Exception e) {
            return handleToolError(e, toolRequest, invocationContext, argumentsErrorHandler, executionErrorHandler);
        }
        return futureToolResult.exceptionallyCompose(error -> {
            Throwable cause = unwrapCompletionException(error);
            if (cause instanceof AsyncNotSupportedException) {
                return CompletableFuture.failedFuture(cause);
            }
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
            return CompletableFuture.completedFuture(toolErrorResult(
                    e, toolRequest, invocationContext, argumentsErrorHandler, executionErrorHandler));
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
            return toolErrorResult(e, toolRequest, invocationContext, argumentsErrorHandler, executionErrorHandler);
        }
    }

    /**
     * Routes a tool failure to the appropriate error handler ({@link ToolArgumentsErrorHandler} for an argument
     * problem, {@link ToolExecutionErrorHandler} otherwise) and turns the handler's response into an error
     * {@link ToolExecutionResult}. Shared by the synchronous and asynchronous execution paths.
     */
    private static ToolExecutionResult toolErrorResult(
            Exception e,
            ToolExecutionRequest toolRequest,
            InvocationContext invocationContext,
            ToolArgumentsErrorHandler argumentsErrorHandler,
            ToolExecutionErrorHandler executionErrorHandler) {

        ToolErrorContext errorContext = ToolErrorContext.builder()
                .toolExecutionRequest(toolRequest)
                .invocationContext(invocationContext)
                .rawError(e)
                .build();

        ToolErrorHandlerResult errorHandlerResult = e instanceof ToolArgumentsException
                ? argumentsErrorHandler.handle(getCause(e), errorContext)
                : executionErrorHandler.handle(getCause(e), errorContext);

        return ToolExecutionResult.builder()
                .isError(true)
                .resultText(errorHandlerResult.text())
                .build();
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
