package dev.langchain4j.agentic.mcp;

import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.internal.AgentInvoker;
import dev.langchain4j.agentic.internal.InternalAgent;
import dev.langchain4j.agentic.internal.McpClientBuilder;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.planner.AgentArgument;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgenticSystemConfigurationException;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.internal.Json;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.service.output.ServiceOutputParser;
import dev.langchain4j.service.tool.ToolExecutionResult;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static dev.langchain4j.agentic.observability.ComposedAgentListener.composeWithInherited;

public class DefaultMcpClientBuilder<T> implements McpClientBuilder<T>, InternalAgent, InvocationHandler {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultMcpClientBuilder.class);

    private final ServiceOutputParser serviceOutputParser = new ServiceOutputParser();

    private final McpClient mcpClient;
    private final Class<T> agentServiceClass;

    private String toolName;
    private String name;
    private String agentId;
    private String description;
    private InternalAgent parent;

    private String[] inputKeys;
    private String outputKey;
    private boolean async;

    private AgentListener agentListener;

    DefaultMcpClientBuilder(McpClient mcpClient, Class<T> agentServiceClass) {
        this.mcpClient = mcpClient;
        this.agentServiceClass = agentServiceClass;
    }

    @Override
    public McpClientBuilder<T> toolName(String toolName) {
        this.toolName = toolName;
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
        this.agentListener = agentListener;
        return this;
    }

    @Override
    public T build() {
        ToolSpecification toolSpec = findTool();

        this.name = toolSpec.name();
        this.agentId = this.name;
        this.description = toolSpec.description();

        if (agentServiceClass == UntypedAgent.class && inputKeys == null) {
            JsonObjectSchema params = toolSpec.parameters();
            if (params != null && params.properties() != null) {
                this.inputKeys = params.properties().keySet().toArray(new String[0]);
            } else {
                this.inputKeys = new String[0];
            }
        }

        Object agent = Proxy.newProxyInstance(
                agentServiceClass.getClassLoader(),
                new Class<?>[] {agentServiceClass, McpClientInstance.class}, this);

        return (T) agent;
    }

    private ToolSpecification findTool() {
        List<ToolSpecification> tools = mcpClient.listTools();
        if (toolName == null || toolName.isBlank()) {
            if (tools.size() == 1) {
                return tools.get(0);
            }
            throw new AgenticSystemConfigurationException(
                    "Tool name is required when there is more than one tool available: " +
                            tools.stream().map(ToolSpecification::name).toList());
        }

        return tools.stream()
                .filter(t -> toolName == null || t.name().equals(toolName))
                .findFirst()
                .orElseThrow(() -> new AgenticSystemConfigurationException(
                        "Tool '" + toolName + "' not found. Available tools: " +
                                tools.stream().map(ToolSpecification::name).toList()));
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Exception {
        if (method.getDeclaringClass() == AgentInstance.class || method.getDeclaringClass() == InternalAgent.class) {
            return method.invoke(Proxy.getInvocationHandler(proxy), args);
        }

        if (method.getDeclaringClass() == McpClientInstance.class) {
            return switch (method.getName()) {
                case "toolName" -> name;
                case "toolDescription" -> description;
                case "inputKeys" -> inputKeys;
                default ->
                        throw new UnsupportedOperationException(
                                "Unknown method on McpClientInstance class: " + method.getName());
            };
        }

        return invokeTool(method, args);
    }

    private static Type getReturnType(Method method) {
        Type type = method.getGenericReturnType();
        return type == Object.class ? String.class : type;
    }

    private Object invokeTool(Method method, Object[] args) {
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
                keys = java.util.stream.Stream.of(method.getParameters())
                        .map(AgentInvoker::parameterName)
                        .toArray(String[]::new);
            }
            for (int i = 0; i < keys.length && i < args.length; i++) {
                argsMap.put(keys[i], args[i]);
            }
        }

        String argumentsJson = Json.toJson(argsMap);

        ToolExecutionRequest executionRequest = ToolExecutionRequest.builder()
                .name(name)
                .arguments(argumentsJson)
                .build();

        ToolExecutionResult result = mcpClient.executeTool(executionRequest);

        if (result.isError()) {
            throw new RuntimeException("MCP tool execution failed: " + result.resultText());
        }

        String responseText = result.resultText();
        LOG.debug("MCP tool '{}' response: {}", name, responseText);

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
        this.agentId = this.agentId + idSuffix;
    }

    @Override
    public AgentListener listener() {
        return agentListener;
    }

    @Override
    public Class<?> type() {
        return null;
    }

    @Override
    public Class<? extends Planner> plannerType() {
        return null;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String agentId() {
        return agentId;
    }

    @Override
    public String description() {
        return description;
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
    public List<AgentArgument> arguments() {
        return List.of();
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
