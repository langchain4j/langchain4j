package dev.langchain4j.agentic.mcp;

import static dev.langchain4j.agentic.observability.ComposedAgentListener.compose;
import static dev.langchain4j.agentic.observability.ComposedAgentListener.composeWithInherited;
import static dev.langchain4j.internal.Utils.isNullOrBlank;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.declarative.McpClientAgent;
import dev.langchain4j.agentic.internal.AgentInvoker;
import dev.langchain4j.agentic.internal.AgentUtil;
import dev.langchain4j.agentic.internal.InternalAgent;
import dev.langchain4j.agentic.internal.LazyResolution;
import dev.langchain4j.agentic.internal.McpClientBuilder;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.planner.AgentArgument;
import dev.langchain4j.agentic.planner.AgentCondition;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgenticSystemConfigurationException;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.internal.Json;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.service.output.ServiceOutputParser;
import dev.langchain4j.service.tool.ToolExecutionResult;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultMcpClientBuilder<T> implements McpClientBuilder<T>, InternalAgent, InvocationHandler {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultMcpClientBuilder.class);

    /**
     * The name of an untyped agent without a configured tool name, whose tool is only known once resolved. Using it
     * logs a warning, and at most one agent of an agentic system can be named with it, since those agents could not
     * be told apart otherwise, as {@link McpClientAgentInvoker} checks.
     */
    static final String PLACEHOLDER_NAME = "mcp-agent";

    private final ServiceOutputParser serviceOutputParser = new ServiceOutputParser();

    private final Class<T> agentServiceClass;
    private final Optional<Method> agentMethod;
    private Supplier<McpClient> mcpClientSupplier;

    // The MCP client is created and the tool resolved lazily, the first time they are needed, so that building an
    // agentic system never depends on the availability of the MCP server
    private volatile McpClient mcpClient;
    private final LazyResolution<ToolSpecification> toolResolution = new LazyResolution<>(
            () -> "the MCP tool of the MCP client agent '" + name() + "'", this::findToolOnServer);
    private Duration resolutionTimeout;

    private String toolName;
    private String name;
    private boolean placeholderName;
    private String agentId;
    private String description;
    private InternalAgent parent;

    private String[] inputKeys;
    private volatile Map<String, String> inputDescriptions = Map.of();
    private volatile List<AgentArgument> arguments = List.of();
    private String outputKey;
    private boolean async;

    private AgentListener agentListener;
    private AgentCondition activationCondition;

    DefaultMcpClientBuilder(Supplier<McpClient> mcpClientSupplier, Class<T> agentServiceClass) {
        this.mcpClientSupplier = mcpClientSupplier;
        this.agentServiceClass = agentServiceClass;
        this.agentMethod = agentMethod(agentServiceClass);
    }

    @Override
    public McpClientBuilder<T> toolName(String toolName) {
        this.toolName = toolName;
        return this;
    }

    @Override
    public McpClientBuilder<T> name(String name) {
        if (!isNullOrBlank(name)) {
            this.name = name;
        }
        return this;
    }

    @Override
    public McpClientBuilder<T> description(String description) {
        if (!isNullOrBlank(description)) {
            this.description = description;
        }
        return this;
    }

    @Override
    public McpClientBuilder<T> inputKeys(String... inputKeys) {
        this.inputKeys = inputKeys;
        return this;
    }

    @Override
    public McpClientBuilder<T> outputKey(String outputKey) {
        this.outputKey = outputKey;
        return this;
    }

    @Override
    public McpClientBuilder<T> async(boolean async) {
        this.async = async;
        return this;
    }

    @Override
    public McpClientBuilder<T> listener(AgentListener agentListener) {
        this.agentListener = compose(this.agentListener, agentListener);
        return this;
    }

    @Override
    public McpClientBuilder<T> activationCondition(String description, Predicate<AgenticScope> activationCondition) {
        this.activationCondition = new AgentCondition(description, activationCondition);
        return this;
    }

    @Override
    public McpClientBuilder<T> resolutionTimeout(Duration resolutionTimeout) {
        if (resolutionTimeout != null && resolutionTimeout.isNegative()) {
            throw new IllegalArgumentException(
                    "The resolution timeout of an MCP client agent must not be negative: " + resolutionTimeout);
        }
        this.resolutionTimeout = resolutionTimeout;
        return this;
    }

    @Override
    public T build() {
        // An untyped agent would report no argument until its tool is resolved, while some planners read the
        // arguments at invocation without resolving it, so its input keys are mandatory, as for an untyped A2A agent
        if (agentServiceClass == UntypedAgent.class && inputKeys == null) {
            throw new IllegalArgumentException("Input keys must be provided for an untyped MCP client agent: "
                    + "set McpClientBuilder.inputKeys(...) with the names of the input parameters of its tool");
        }
        if (description == null) {
            description = agentMethod.map(DefaultMcpClientBuilder::annotatedDescription).orElse(null);
        }
        // The local name, and the agent id derived from it, are fixed before the proxy is handed out, and the
        // arguments are the local ones until the tool is resolved
        if (name == null) {
            name = defaultName();
        }
        agentId = name;
        if (placeholderName) {
            LOG.warn(
                    "The MCP client agent has neither a name nor a tool name and is named with the placeholder '{}': "
                            + "set McpClientBuilder.name(...) or toolName(...) to give it a meaningful name, "
                            + "which is mandatory when an agentic system contains more than one such agent",
                    PLACEHOLDER_NAME);
        }
        this.arguments = localArguments();

        Object agent = Proxy.newProxyInstance(
                agentServiceClass.getClassLoader(), new Class<?>[] {agentServiceClass, McpClientInstance.class}, this);

        return (T) agent;
    }

    /**
     * The arguments known without the tool: those of the agent method of a typed agent, or the mandatory input keys
     * of an untyped one, without type information.
     */
    private List<AgentArgument> localArguments() {
        if (agentServiceClass != UntypedAgent.class) {
            return typedArguments();
        }
        return Stream.of(inputKeys).map(key -> new AgentArgument(Object.class, key)).toList();
    }

    private List<AgentArgument> typedArguments() {
        return agentMethod.map(AgentUtil::argumentsFromMethod).orElse(List.of());
    }

    private static Optional<Method> agentMethod(Class<?> agentServiceClass) {
        if (agentServiceClass == UntypedAgent.class) {
            return Optional.empty();
        }
        Optional<Method> agentMethod = AgentUtil.getAnnotatedMethodOnClass(agentServiceClass, Agent.class)
                .or(() -> AgentUtil.getAnnotatedMethodOnClass(agentServiceClass, McpClientAgent.class));
        if (agentMethod.isPresent()) {
            return agentMethod;
        }
        List<Method> abstractMethods = Stream.of(agentServiceClass.getMethods())
                .filter(method -> Modifier.isAbstract(method.getModifiers()))
                .toList();
        return abstractMethods.size() == 1 ? Optional.of(abstractMethods.get(0)) : Optional.empty();
    }

    private static String annotatedName(Method agentMethod) {
        Agent agent = agentMethod.getAnnotation(Agent.class);
        if (agent != null) {
            return agent.name();
        }
        McpClientAgent mcpClientAgent = agentMethod.getAnnotation(McpClientAgent.class);
        return mcpClientAgent != null ? mcpClientAgent.name() : null;
    }

    private static String annotatedDescription(Method agentMethod) {
        Agent agent = agentMethod.getAnnotation(Agent.class);
        if (agent != null) {
            return firstNonBlank(agent.description(), agent.value());
        }
        McpClientAgent mcpClientAgent = agentMethod.getAnnotation(McpClientAgent.class);
        return mcpClientAgent != null ? firstNonBlank(mcpClientAgent.description(), mcpClientAgent.value()) : null;
    }

    private static String firstNonBlank(String first, String second) {
        if (!isNullOrBlank(first)) {
            return first;
        }
        return isNullOrBlank(second) ? null : second;
    }

    /**
     * Returns the tool specification, creating the MCP client and listing the tools of the server the first time, on
     * a thread of the LangChain4j executor and within the resolution timeout, when one is set. Concurrent first
     * invocations wait on a single resolution and all fail when it fails, while a failed resolution is not cached,
     * so that the next invocation retries it.
     */
    private ToolSpecification resolveTool() {
        try {
            return toolResolution.get(resolutionTimeout);
        } catch (TimeoutException e) {
            throw new McpToolResolutionException(name(), configuredToolName(), resolutionTimeout, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new McpToolResolutionException(name(), configuredToolName(), e);
        } catch (AgenticSystemConfigurationException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new McpToolResolutionException(name(), configuredToolName(), e);
        }
    }

    /**
     * Resolves the tool, on the thread doing it for every invocation waiting. The client is kept as soon as it is
     * created, so that a failure or a timeout of the listing of the tools does not create another one the next time.
     */
    private ToolSpecification findToolOnServer() {
        McpClient client = mcpClient;
        if (client == null) {
            client = mcpClientSupplier.get();
            if (client == null) {
                throw new AgenticSystemConfigurationException(
                        "The MCP client supplier of the MCP client agent '" + name() + "' returned null");
            }
            mcpClient = client;
            mcpClientSupplier = null;
        }
        ToolSpecification resolvedTool = findTool(client.listTools());
        applyTool(resolvedTool);
        return resolvedTool;
    }

    private ToolSpecification findTool(List<ToolSpecification> tools) {
        String configuredToolName = configuredToolName();
        if (configuredToolName == null) {
            if (tools.size() == 1) {
                return tools.get(0);
            }
            throw new AgenticSystemConfigurationException(
                    "Tool name is required when there is more than one tool available: "
                            + tools.stream().map(ToolSpecification::name).toList());
        }

        return tools.stream()
                .filter(t -> t.name().equals(configuredToolName))
                .findFirst()
                .orElseThrow(() -> new AgenticSystemConfigurationException("Tool '" + configuredToolName
                        + "' not found. Available tools: "
                        + tools.stream().map(ToolSpecification::name).toList()));
    }

    /** Fills in what is known only from the tool: the argument descriptions and, for an untyped agent, the types. */
    private void applyTool(ToolSpecification resolvedTool) {
        JsonObjectSchema params = resolvedTool.parameters();
        Map<String, String> descriptions = new HashMap<>();
        if (params != null && params.properties() != null) {
            params.properties().forEach((key, schema) -> {
                if (schema != null
                        && schema.description() != null
                        && !schema.description().isBlank()) {
                    descriptions.put(key, schema.description());
                }
            });
        }

        inputDescriptions = Map.copyOf(descriptions);
        arguments = agentServiceClass == UntypedAgent.class
                ? McpSchemaToType.arguments(params, inputKeys)
                : McpSchemaToType.mergeDescriptions(typedArguments(), inputDescriptions);
    }

    private String configuredToolName() {
        return isNullOrBlank(toolName) ? null : toolName;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Exception {
        if (method.getDeclaringClass() == AgentInstance.class || method.getDeclaringClass() == InternalAgent.class) {
            try {
                return method.invoke(Proxy.getInvocationHandler(proxy), args);
            } catch (InvocationTargetException e) {
                if (e.getCause() instanceof Error error) {
                    throw error;
                }
                throw (Exception) e.getCause();
            }
        }

        if (method.getDeclaringClass() == McpClientInstance.class) {
            return switch (method.getName()) {
                case "toolSpecification" -> resolveTool();
                case "toolName" -> toolName();
                case "toolDescription" -> resolveTool().description();
                case "inputKeys" -> inputKeys;
                case "inputDescriptions" -> inputDescriptions();
                default ->
                    throw new UnsupportedOperationException(
                            "Unknown method on McpClientInstance class: " + method.getName());
            };
        }

        return invokeTool(method, args);
    }

    /** The configured tool name, else the name of the single tool of the server, resolved at this point. */
    private String toolName() {
        String configuredToolName = configuredToolName();
        return configuredToolName != null ? configuredToolName : resolveTool().name();
    }

    private Map<String, String> inputDescriptions() {
        resolveTool();
        return inputDescriptions;
    }

    private static Type getReturnType(Method method) {
        Type type = method.getGenericReturnType();
        return type == Object.class ? String.class : type;
    }

    private Object invokeTool(Method method, Object[] args) {
        ToolSpecification resolvedTool = resolveTool();
        Type returnType = getReturnType(method);
        Map<String, Object> argsMap = new HashMap<>();

        if (agentServiceClass == UntypedAgent.class) {
            Map<String, Object> params = (Map<String, Object>) args[0];
            for (String inputKey : inputKeys) {
                argsMap.put(inputKey, params.get(inputKey));
            }
        } else {
            String[] keys = inputKeys;
            if (keys == null) {
                keys = Stream.of(method.getParameters())
                        .map(AgentInvoker::parameterName)
                        .toArray(String[]::new);
            }
            for (int i = 0; i < keys.length && i < args.length; i++) {
                argsMap.put(keys[i], args[i]);
            }
        }

        String argumentsJson = Json.toJson(argsMap);

        ToolExecutionRequest executionRequest = ToolExecutionRequest.builder()
                .name(resolvedTool.name())
                .arguments(argumentsJson)
                .build();

        ToolExecutionResult result = mcpClient.executeTool(executionRequest);

        if (result.isError()) {
            throw new RuntimeException("MCP tool execution failed: " + result.resultText());
        }

        String responseText = result.resultText();
        LOG.debug("MCP tool '{}' response: {}", resolvedTool.name(), responseText);

        return serviceOutputParser.parseText(returnType, responseText);
    }

    @Override
    public void setParent(InternalAgent parent) {
        this.parent = parent;
    }

    @Override
    public void registerInheritedParentListener(AgentListener parentListener) {
        if (parentListener != null && parentListener.inheritedBySubagents()) {
            agentListener = composeWithInherited(listener(), parentListener);
        }
    }

    @Override
    public void appendId(String idSuffix) {
        this.agentId = agentId + idSuffix;
    }

    @Override
    public AgentListener listener() {
        return agentListener;
    }

    @Override
    public Class<?> type() {
        return agentServiceClass;
    }

    @Override
    public Class<? extends Planner> plannerType() {
        return null;
    }

    /**
     * The name, fixed by {@link #build()}, is always known locally, without contacting the MCP server: it is the
     * explicit one, else the one of the annotation of the agent method, else the name of that method for a typed
     * agent, else the configured tool name for an untyped agent, else a placeholder. It never changes afterwards, not
     * even when the tool is resolved, because the agent id is derived from it.
     */
    @Override
    public String name() {
        return name;
    }

    private String defaultName() {
        if (agentMethod.isPresent()) {
            String annotatedName = annotatedName(agentMethod.get());
            return isNullOrBlank(annotatedName) ? agentMethod.get().getName() : annotatedName;
        }
        String configuredToolName = configuredToolName();
        if (configuredToolName != null) {
            return configuredToolName;
        }
        placeholderName = true;
        return PLACEHOLDER_NAME;
    }

    @Override
    public String agentId() {
        return agentId;
    }

    /**
     * The explicit description, else the one of the MCP tool, which is resolved at this point if not yet done.
     */
    @Override
    public String description() {
        return description != null ? description : resolveTool().description();
    }

    @Override
    public Type outputType() {
        return Object.class;
    }

    @Override
    public String outputKey() {
        return outputKey;
    }

    @Override
    public boolean async() {
        return async;
    }

    @Override
    public AgentCondition activationCondition() {
        return activationCondition;
    }

    /**
     * The arguments never contact the MCP server, because every workflow reads them when it is built: they are the
     * local ones until the tool is resolved, and carry the types, optionality and descriptions of its input schema
     * afterwards.
     */
    @Override
    public List<AgentArgument> arguments() {
        return arguments;
    }

    @Override
    public AgentInstance parent() {
        return parent;
    }

    @Override
    public List<AgentInstance> subagents() {
        return List.of();
    }

    @Override
    public AgenticSystemTopology topology() {
        return AgenticSystemTopology.NON_AI_AGENT;
    }
}
