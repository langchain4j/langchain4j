package dev.langchain4j.service.tool;

import static dev.langchain4j.agent.tool.ReturnBehavior.IMMEDIATE;
import static dev.langchain4j.agent.tool.ReturnBehavior.IMMEDIATE_IF_LAST;
import static dev.langchain4j.agent.tool.ToolSpecifications.toolSpecificationFrom;
import static dev.langchain4j.internal.Exceptions.runtime;
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
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.ToolArgumentsException;
import dev.langchain4j.internal.DefaultExecutorProvider;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.observability.api.AiServiceListenerRegistrar;
import dev.langchain4j.observability.api.event.AiServiceRequestIssuedEvent;
import dev.langchain4j.observability.api.event.AiServiceResponseReceivedEvent;
import dev.langchain4j.observability.api.event.ToolExecutedEvent;
import dev.langchain4j.service.AiServiceContext;
import dev.langchain4j.service.IllegalConfigurationException;
import dev.langchain4j.service.Result;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

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
    private int maxSequentialToolsInvocations = 100;
    private int maxToolCallsPerResponse = 0;
    private ToolArgumentsErrorHandler argumentsErrorHandler;
    private ToolExecutionErrorHandler executionErrorHandler;
    private Function<ToolExecutionRequest, ToolExecutionResultMessage> toolHallucinationStrategy =
            HallucinatedToolNameStrategy.THROW_EXCEPTION;
    private ToolSearchService toolSearchService;

    private Consumer<BeforeToolExecution> beforeToolExecution = null;
    private Consumer<ToolExecution> afterToolExecution = null;

    private boolean forceToolChoiceAutoAfterFirstIteration = false;
    private Predicate<Throwable> errorHandlerBypass = e -> false;
    private Function<ToolProviderRequest.Builder, ToolProviderRequest> toolProviderRequestFactory =
            ToolProviderRequest.Builder::build;

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
            if (!parameter.getType().isPrimitive()) {
                continue;
            }
            P pAnnotation = parameter.getAnnotation(P.class);
            if (pAnnotation != null && !pAnnotation.required()) {
                throw illegalConfiguration(
                        "Parameter '%s' of tool '%s.%s' is a primitive (%s) and cannot be marked as @P(required = false). "
                                + "Use a boxed type (e.g. Integer instead of int) or Optional<T> to allow optional values.",
                        parameter.getName(),
                        toolMethod.getDeclaringClass().getName(),
                        toolMethod.getName(),
                        parameter.getType().getName());
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

    public void maxSequentialToolsInvocations(int maxSequentialToolsInvocations) {
        this.maxSequentialToolsInvocations = maxSequentialToolsInvocations;
    }

    public int maxSequentialToolsInvocations() {
        return maxSequentialToolsInvocations;
    }

    /**
     * Sets the maximum number of tool execution requests allowed within a single LLM response.
     *
     * <p>
     * Intended for cooperative truncation when an LLM returns more tool calls in a single
     * response than the user wants to spend. When an LLM response contains more than this
     * many tool execution requests, a {@link ToolCallsLimitExceededException} is thrown
     * before any tool is executed for that response.
     *
     * <p>
     * A value of {@code 0} (the default) means unlimited — no cap is enforced.
     *
     * @param maxToolCallsPerResponse the maximum number of tool execution requests permitted
     *                                in a single LLM response, or {@code 0} for unlimited
     * @since 1.14.0
     */
    public void maxToolCallsPerResponse(int maxToolCallsPerResponse) {
        this.maxToolCallsPerResponse = maxToolCallsPerResponse;
    }

    /**
     * @return the maximum number of tool execution requests permitted in a single LLM response,
     *         or {@code 0} for unlimited
     * @since 1.14.0
     */
    public int maxToolCallsPerResponse() {
        return maxToolCallsPerResponse;
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
     * Controls whether {@link ToolChoice#REQUIRED} is automatically rewritten to {@link ToolChoice#AUTO}
     * after the first iteration of {@link #executeInferenceAndToolsLoop}.
     *
     * <p>When the LLM↔tools loop reuses the original {@link ChatRequestParameters} on follow-up
     * iterations, a caller-supplied {@link ToolChoice#REQUIRED} would force the model to keep
     * calling tools forever. This flag is intended for downstream framework integrators
     * (e.g. {@code quarkus-langchain4j}) that want to set {@code REQUIRED} once on the very
     * first request but allow the model to terminate the loop on subsequent iterations.
     *
     * <p>Default is {@code false}, which preserves the original behavior of forwarding the
     * caller-supplied {@link ToolChoice} on every iteration.
     *
     * @param forceToolChoiceAutoAfterFirstIteration if {@code true}, the loop rewrites
     *                                               {@link ToolChoice#REQUIRED} to {@link ToolChoice#AUTO}
     *                                               on every iteration after the first.
     * @since 1.14.0
     */
    public void forceToolChoiceAutoAfterFirstIteration(boolean forceToolChoiceAutoAfterFirstIteration) {
        this.forceToolChoiceAutoAfterFirstIteration = forceToolChoiceAutoAfterFirstIteration;
    }

    /**
     * @return whether {@link ToolChoice#REQUIRED} is rewritten to {@link ToolChoice#AUTO}
     *         after the first iteration of the inference loop.
     * @since 1.14.0
     */
    public boolean forceToolChoiceAutoAfterFirstIteration() {
        return forceToolChoiceAutoAfterFirstIteration;
    }

    /**
     * Configures a predicate that, when it evaluates to {@code true} for a tool execution
     * exception, causes the exception to propagate unchanged instead of being routed
     * through the configured {@link ToolExecutionErrorHandler}.
     *
     * <p>This hook is intended for downstream framework integrators that need to let
     * specific marker-typed exceptions (e.g. authorization or guardrail violations)
     * surface to the caller verbatim rather than be summarized into a string sent back
     * to the LLM.
     *
     * <p>The default predicate returns {@code false} for all throwables, which preserves
     * the original behavior of routing every exception through the error handler.
     *
     * @param errorHandlerBypass a predicate; when {@code true} the exception is rethrown
     *                           unchanged. Must not be {@code null}.
     * @since 1.14.0
     */
    public void errorHandlerBypass(Predicate<Throwable> errorHandlerBypass) {
        this.errorHandlerBypass = errorHandlerBypass == null ? e -> false : errorHandlerBypass;
    }

    /**
     * @return the predicate that controls whether a tool execution exception bypasses
     *         the error handler. The default predicate returns {@code false} for all throwables.
     * @since 1.14.0
     */
    public Predicate<Throwable> errorHandlerBypass() {
        return errorHandlerBypass;
    }

    /**
     * Configures a factory used to build the {@link ToolProviderRequest} passed to
     * {@link ToolProvider}s, both during initial context creation and dynamic refresh.
     *
     * <p>This hook is intended for downstream framework integrators that need to attach
     * additional context to the {@link ToolProviderRequest}, typically by returning a
     * subclass enriched with framework-specific attributes. Tool providers can then
     * downcast the request to access those attributes.
     *
     * <p>The default factory invokes {@link ToolProviderRequest.Builder#build()},
     * which preserves the original behavior of constructing a plain {@link ToolProviderRequest}.
     *
     * @param toolProviderRequestFactory a factory that consumes a fully populated
     *                                   {@link ToolProviderRequest.Builder} and returns the
     *                                   {@link ToolProviderRequest} to pass to tool providers.
     *                                   Must not be {@code null}.
     * @since 1.14.0
     */
    public void toolProviderRequestFactory(
            Function<ToolProviderRequest.Builder, ToolProviderRequest> toolProviderRequestFactory) {
        this.toolProviderRequestFactory =
                toolProviderRequestFactory == null ? ToolProviderRequest.Builder::build : toolProviderRequestFactory;
    }

    /**
     * @return the factory used to build the {@link ToolProviderRequest} passed to tool providers.
     * @since 1.14.0
     */
    public Function<ToolProviderRequest.Builder, ToolProviderRequest> toolProviderRequestFactory() {
        return toolProviderRequestFactory;
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
        ToolServiceContext context = createContextFromStaticToolsAndProviders(invocationContext, userMessage, messages);
        if (toolSearchService != null) {
            context = toolSearchService.adjust(context, messages, invocationContext);
        }
        context = refreshDynamicProvidersWithFactory(context, messages, invocationContext);
        return context;
    }

    private ToolServiceContext createContextFromStaticToolsAndProviders(InvocationContext invocationContext,
                                                                        UserMessage userMessage,
                                                                        List<ChatMessage> messages) {
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

        ToolProviderRequest toolProviderRequest = toolProviderRequestFactory.apply(ToolProviderRequest.builder()
                .invocationContext(invocationContext)
                .userMessage(userMessage)
                .messages(messages));
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

    private static void addTools(List<AiServiceTool> tools,
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
                context == null ? null : context.chatModel,
                memoryId,
                chatResponse,
                parameters,
                messages,
                chatMemory,
                invocationContext,
                toolServiceContext,
                isReturnTypeResult);
    }

    /**
     * Variant of {@link #executeInferenceAndToolsLoop(AiServiceContext, Object, ChatResponse,
     * ChatRequestParameters, List, ChatMemory, InvocationContext, ToolServiceContext, boolean)}
     * that uses an explicit {@link ChatModel} for follow-up inference requests inside the loop,
     * instead of {@code context.chatModel}.
     *
     * <p>This overload is intended for downstream framework integrators (e.g. {@code quarkus-langchain4j})
     * that select the {@link ChatModel} per AI service method via mechanisms such as a
     * {@code @ModelName} qualifier resolved against the method's arguments. The chat model
     * supplied here is used for every iteration after the initial response that the caller
     * already passed in via {@code chatResponse}.
     *
     * @param chatModel the {@link ChatModel} to use for follow-up inference requests inside
     *                  the loop. If {@code null}, falls back to {@code context.chatModel}.
     * @since 1.14.0
     */
    public ToolServiceResult executeInferenceAndToolsLoop(
            AiServiceContext context,
            ChatModel chatModel,
            Object memoryId,
            ChatResponse chatResponse,
            ChatRequestParameters parameters,
            List<ChatMessage> messages,
            ChatMemory chatMemory,
            InvocationContext invocationContext,
            ToolServiceContext toolServiceContext,
            boolean isReturnTypeResult) {
        ChatModel effectiveChatModel = chatModel != null ? chatModel : context.chatModel;

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

            List<ToolExecutionRequest> toolExecutionRequests = aiMessage.toolExecutionRequests();
            if (maxToolCallsPerResponse > 0 && toolExecutionRequests.size() > maxToolCallsPerResponse) {
                throw new ToolCallsLimitExceededException(maxToolCallsPerResponse, toolExecutionRequests.size());
            }

            intermediateResponses.add(chatResponse);

            Map<ToolExecutionRequest, ToolExecutionResult> toolResults =
                    execute(toolExecutionRequests, toolServiceContext.toolExecutors(), invocationContext);

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

            if (shouldReturnImmediately(anyToolErrored, returnBehaviors)) {
                if (!isReturnTypeResult) {
                    throw illegalConfiguration(
                            "AI Service method must return a %s type to use tools with ReturnBehavior.%s/%s",
                            Result.class.getName(), IMMEDIATE, IMMEDIATE_IF_LAST);
                }
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
                if (!context.storeRetrievedContentInChatMemory) {
                    messages = UserMessage.replaceLast(chatMemory.messages(), invocationContext.userMessage());
                }
            }

            toolServiceContext = refreshDynamicProvidersWithFactory(toolServiceContext, messages, invocationContext);
            if (toolSearchService != null) {
                toolServiceContext = ToolSearchService.addFoundTools(toolServiceContext, toolResults.values());
            }

            // Hook 2: opt-in self-protection against infinite loops when the caller set
            // ToolChoice.REQUIRED. Iteration 0 has just completed (we are about to send
            // iteration 1), so from this point on the original REQUIRED is rewritten to AUTO
            // to let the LLM terminate the loop.
            ChatRequestParameters override;
            if (forceToolChoiceAutoAfterFirstIteration && parameters.toolChoice() == ToolChoice.REQUIRED) {
                override = ChatRequestParameters.builder()
                        .toolSpecifications(toolServiceContext.effectiveTools())
                        .toolChoice(ToolChoice.AUTO)
                        .build();
            } else {
                override = ChatRequestParameters.builder()
                        .toolSpecifications(toolServiceContext.effectiveTools())
                        .build();
            }
            parameters = parameters.overrideWith(override);

            ChatRequest chatRequest = context.chatRequestTransformer.apply(
                    ChatRequest.builder()
                            .messages(messages)
                            .parameters(parameters)
                            .build(),
                    memoryId);

            fireRequestIssuedEvent(chatRequest, invocationContext, context.eventListenerRegistrar);
            chatResponse = effectiveChatModel.chat(chatRequest);
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

    public static boolean shouldReturnImmediately(boolean anyToolErrored, List<ReturnBehavior> returnBehaviors) {
        if (anyToolErrored) {
            return false; // if any tool call failed, LLM should receive an error so that it can attempt to fix it
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
     * <p>This static variant uses a default {@link ToolProviderRequest.Builder#build()} factory.
     * Frameworks that need to inject a custom {@link ToolProviderRequest} subclass should use
     * {@link #refreshDynamicProvidersWithFactory(ToolServiceContext, List, InvocationContext)} on a
     * configured {@link ToolService} instance instead.
     *
     * @since 1.13.0
     */
    public static ToolServiceContext refreshDynamicProviders(ToolServiceContext toolServiceContext,
                                                             List<ChatMessage> messages,
                                                             InvocationContext invocationContext) {
        return doRefreshDynamicProviders(
                toolServiceContext, messages, invocationContext, ToolProviderRequest.Builder::build);
    }

    /**
     * Instance variant of {@link #refreshDynamicProviders(ToolServiceContext, List, InvocationContext)}
     * that uses the {@link #toolProviderRequestFactory()} configured on this service to build the
     * {@link ToolProviderRequest} passed to dynamic providers.
     *
     * @since 1.14.0
     */
    public ToolServiceContext refreshDynamicProvidersWithFactory(ToolServiceContext toolServiceContext,
                                                                 List<ChatMessage> messages,
                                                                 InvocationContext invocationContext) {
        return doRefreshDynamicProviders(
                toolServiceContext, messages, invocationContext, toolProviderRequestFactory);
    }

    private static ToolServiceContext doRefreshDynamicProviders(
            ToolServiceContext toolServiceContext,
            List<ChatMessage> messages,
            InvocationContext invocationContext,
            Function<ToolProviderRequest.Builder, ToolProviderRequest> requestFactory) {
        if (toolServiceContext == null) {
            return null;
        }

        List<ToolProvider> dynamicProviders = toolServiceContext.dynamicToolProviders();
        if (dynamicProviders.isEmpty()) {
            return toolServiceContext;
        }

        UserMessage userMessage = UserMessage.findLast(messages).orElseThrow();
        ToolProviderRequest request = requestFactory.apply(ToolProviderRequest.builder()
                .invocationContext(invocationContext)
                .userMessage(userMessage)
                .messages(messages));

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
                        toolRequest,
                        executor,
                        invocationContext,
                        argumentsErrorHandler(),
                        executionErrorHandler(),
                        errorHandlerBypass);

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
        return executeWithErrorHandling(
                toolRequest, toolExecutor, invocationContext, argumentsErrorHandler, executionErrorHandler, e -> false);
    }

    /**
     * Variant of {@link #executeWithErrorHandling(ToolExecutionRequest, ToolExecutor, InvocationContext,
     * ToolArgumentsErrorHandler, ToolExecutionErrorHandler)} that also accepts a predicate which,
     * when it evaluates to {@code true} for a thrown exception, causes that exception to propagate
     * unchanged instead of being routed through the configured error handlers.
     *
     * <p>This overload is intended for downstream framework integrators that need to let
     * specific marker-typed exceptions surface to the caller verbatim.
     *
     * @param errorHandlerBypass a predicate; when {@code true} for the thrown exception, the
     *                           exception is rethrown unchanged. May be {@code null}, in which
     *                           case no exception bypasses the error handlers.
     * @since 1.14.0
     */
    public static ToolExecutionResult executeWithErrorHandling(
            ToolExecutionRequest toolRequest,
            ToolExecutor toolExecutor,
            InvocationContext invocationContext,
            ToolArgumentsErrorHandler argumentsErrorHandler,
            ToolExecutionErrorHandler executionErrorHandler,
            Predicate<Throwable> errorHandlerBypass) {
        try {
            return toolExecutor.executeWithContext(toolRequest, invocationContext);
        } catch (Exception e) {
            if (errorHandlerBypass != null && errorHandlerBypass.test(e)) {
                if (e instanceof RuntimeException re) {
                    throw re;
                }
                throw new RuntimeException(e);
            }

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
