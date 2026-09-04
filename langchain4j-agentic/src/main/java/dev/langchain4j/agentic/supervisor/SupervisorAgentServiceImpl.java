package dev.langchain4j.agentic.supervisor;

import static dev.langchain4j.agentic.declarative.DeclarativeUtil.agenticScopeFunction;
import static dev.langchain4j.agentic.declarative.DeclarativeUtil.buildAgentFeatures;
import static dev.langchain4j.agentic.declarative.DeclarativeUtil.invokeStatic;
import static dev.langchain4j.agentic.declarative.DeclarativeUtil.selectMethod;
import static dev.langchain4j.agentic.internal.AgentUtil.validateAgentClass;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agentic.declarative.ChatMemoryProviderSupplier;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.agentic.declarative.Output;
import dev.langchain4j.agentic.declarative.SupervisorRequest;
import dev.langchain4j.agentic.internal.AbstractServiceBuilder;
import dev.langchain4j.agentic.planner.AgenticService;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.tool.ToolArgumentsErrorHandler;
import dev.langchain4j.service.tool.ToolExecutionErrorHandler;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Function;

public class SupervisorAgentServiceImpl<T> extends AbstractServiceBuilder<T, SupervisorAgentServiceImpl<T>>
        implements SupervisorAgentService<T>, AgenticService<SupervisorAgentService<T>, T> {

    private ChatModel chatModel;

    private ChatMemoryProvider chatMemoryProvider;

    private int maxAgentsInvocations = 10;

    private SupervisorContextStrategy contextStrategy = SupervisorContextStrategy.CHAT_MEMORY;
    private SupervisorResponseStrategy responseStrategy = SupervisorResponseStrategy.LAST;

    private Function<AgenticScope, String> requestGenerator;
    private String supervisorContext;

    private List<Object> objectsWithTools = List.of();
    private Map<ToolSpecification, ToolExecutor> toolsMap = Map.of();
    private Set<String> immediateReturnToolNames = Set.of();
    private final List<ToolProvider> toolProviders = new ArrayList<>();
    private Integer maxToolCallingRoundTrips;
    private Function<ToolExecutionRequest, ToolExecutionResultMessage> hallucinatedToolNameStrategy;
    private boolean executeToolsConcurrently;
    private Executor concurrentToolsExecutor;
    private ToolArgumentsErrorHandler toolArgumentsErrorHandler;
    private ToolExecutionErrorHandler toolExecutionErrorHandler;

    public SupervisorAgentServiceImpl(Class<T> agentServiceClass, Method agenticMethod) {
        this(agentServiceClass, agenticMethod, null);
    }

    public SupervisorAgentServiceImpl(Class<T> agentServiceClass, Method agenticMethod, ChatModel chatModel) {
        super(agentServiceClass, agenticMethod);
        configureSupervisor(agentServiceClass, chatModel);
    }

    public T build() {
        if (supervisorContext != null) {
            this.beforeCall(this.beforeCall.andThen(agenticScope ->
                    agenticScope.writeStateIfAbsent(SupervisorPlanner.SUPERVISOR_CONTEXT_KEY, supervisorContext)));
        }

        return build(() -> new SupervisorPlanner(
                chatModel,
                chatMemoryProvider,
                maxAgentsInvocations,
                contextStrategy,
                responseStrategy,
                requestGenerator,
                outputKey,
                output,
                new SupervisorTools(
                        objectsWithTools,
                        toolsMap,
                        immediateReturnToolNames,
                        toolProviders,
                        maxToolCallingRoundTrips,
                        hallucinatedToolNameStrategy,
                        executeToolsConcurrently,
                        concurrentToolsExecutor,
                        toolArgumentsErrorHandler,
                        toolExecutionErrorHandler)));
    }

    public static SupervisorAgentService<SupervisorAgent> builder() {
        try {
            Method supervisorMethod = SupervisorAgent.class.getMethod("invoke", String.class);
            return new SupervisorAgentServiceImpl<>(SupervisorAgent.class, supervisorMethod);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> SupervisorAgentService<T> builder(Class<T> agentServiceClass) {
        return new SupervisorAgentServiceImpl<>(agentServiceClass, validateAgentClass(agentServiceClass, false));
    }

    @Override
    public SupervisorAgentServiceImpl<T> chatModel(ChatModel chatModel) {
        this.chatModel = chatModel;
        return this;
    }

    @Override
    public SupervisorAgentServiceImpl<T> chatMemoryProvider(ChatMemoryProvider chatMemoryProvider) {
        this.chatMemoryProvider = chatMemoryProvider;
        return this;
    }

    @Override
    public SupervisorAgentServiceImpl<T> requestGenerator(Function<AgenticScope, String> requestGenerator) {
        this.requestGenerator = requestGenerator;
        return this;
    }

    @Override
    public SupervisorAgentServiceImpl<T> contextGenerationStrategy(SupervisorContextStrategy contextStrategy) {
        this.contextStrategy = contextStrategy;
        return this;
    }

    @Override
    public SupervisorAgentServiceImpl<T> responseStrategy(SupervisorResponseStrategy responseStrategy) {
        this.responseStrategy = responseStrategy;
        return this;
    }

    @Override
    public SupervisorAgentServiceImpl<T> supervisorContext(String supervisorContext) {
        this.supervisorContext = supervisorContext;
        return this;
    }

    @Override
    public SupervisorAgentServiceImpl<T> maxAgentsInvocations(int maxAgentsInvocations) {
        this.maxAgentsInvocations = maxAgentsInvocations;
        return this;
    }

    @Override
    public SupervisorAgentServiceImpl<T> tools(Object... objectsWithTools) {
        this.objectsWithTools = Arrays.asList(objectsWithTools);
        return this;
    }

    @Override
    public SupervisorAgentServiceImpl<T> tools(Map<ToolSpecification, ToolExecutor> toolsMap) {
        this.toolsMap = toolsMap;
        return this;
    }

    @Override
    public SupervisorAgentServiceImpl<T> tools(
            Map<ToolSpecification, ToolExecutor> toolsMap, Set<String> immediateReturnToolNames) {
        this.toolsMap = toolsMap;
        this.immediateReturnToolNames = immediateReturnToolNames;
        return this;
    }

    @Override
    public SupervisorAgentServiceImpl<T> toolProvider(ToolProvider toolProvider) {
        this.toolProviders.add(toolProvider);
        return this;
    }

    @Override
    public SupervisorAgentServiceImpl<T> toolProviders(Collection<ToolProvider> toolProviders) {
        this.toolProviders.addAll(toolProviders);
        return this;
    }

    @Override
    public SupervisorAgentServiceImpl<T> toolProviders(ToolProvider... toolProviders) {
        return toolProviders(Arrays.asList(toolProviders));
    }

    @Override
    public SupervisorAgentServiceImpl<T> maxToolCallingRoundTrips(int maxToolCallingRoundTrips) {
        this.maxToolCallingRoundTrips = maxToolCallingRoundTrips;
        return this;
    }

    @Override
    public SupervisorAgentServiceImpl<T> hallucinatedToolNameStrategy(
            Function<ToolExecutionRequest, ToolExecutionResultMessage> hallucinatedToolNameStrategy) {
        this.hallucinatedToolNameStrategy = hallucinatedToolNameStrategy;
        return this;
    }

    @Override
    public SupervisorAgentServiceImpl<T> executeToolsConcurrently() {
        this.executeToolsConcurrently = true;
        return this;
    }

    @Override
    public SupervisorAgentServiceImpl<T> executeToolsConcurrently(Executor executor) {
        this.executeToolsConcurrently = true;
        this.concurrentToolsExecutor = executor;
        return this;
    }

    @Override
    public SupervisorAgentServiceImpl<T> toolArgumentsErrorHandler(
            ToolArgumentsErrorHandler toolArgumentsErrorHandler) {
        this.toolArgumentsErrorHandler = toolArgumentsErrorHandler;
        return this;
    }

    @Override
    public SupervisorAgentServiceImpl<T> toolExecutionErrorHandler(
            ToolExecutionErrorHandler toolExecutionErrorHandler) {
        this.toolExecutionErrorHandler = toolExecutionErrorHandler;
        return this;
    }

    @Override
    public String serviceType() {
        return "Supervisor";
    }

    private void configureSupervisor(Class<T> agentServiceClass, ChatModel chatModel) {
        selectMethod(
                        agentServiceClass,
                        method -> method.isAnnotationPresent(SupervisorRequest.class)
                                && method.getReturnType() == String.class)
                .map(m -> agenticScopeFunction(m, String.class))
                .ifPresent(this::requestGenerator);

        selectMethod(
                        agentServiceClass,
                        method -> method.isAnnotationPresent(ChatModelSupplier.class)
                                && method.getReturnType() == ChatModel.class
                                && method.getParameterCount() == 0)
                .map(method -> (ChatModel) invokeStatic(method))
                .ifPresentOrElse(this::chatModel, () -> this.chatModel(chatModel));

        selectMethod(
                        agentServiceClass,
                        method -> method.isAnnotationPresent(ChatMemoryProviderSupplier.class)
                                && method.getReturnType() == ChatMemory.class
                                && method.getParameterCount() == 1)
                .map(method -> (ChatMemoryProvider) memoryId -> invokeStatic(method, memoryId))
                .ifPresent(this::chatMemoryProvider);

        selectMethod(agentServiceClass, method -> method.isAnnotationPresent(Output.class))
                .map(m -> agenticScopeFunction(m, Object.class))
                .ifPresent(this::output);

        buildAgentFeatures(agentServiceClass, this);
    }
}
