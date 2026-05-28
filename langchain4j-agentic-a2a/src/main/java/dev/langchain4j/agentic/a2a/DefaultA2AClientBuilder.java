package dev.langchain4j.agentic.a2a;

import static dev.langchain4j.agentic.observability.ComposedAgentListener.composeWithInherited;

import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.declarative.A2AContextId;
import dev.langchain4j.agentic.declarative.A2ATaskId;
import dev.langchain4j.agentic.internal.A2AClientBuilder;
import dev.langchain4j.agentic.internal.AgentInvoker;
import dev.langchain4j.agentic.internal.InternalAgent;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.planner.AgentArgument;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.invocation.LangChain4jManaged;
import dev.langchain4j.service.output.ServiceOutputParser;
import io.a2a.A2A;
import io.a2a.client.Client;
import io.a2a.client.ClientEvent;
import io.a2a.client.MessageEvent;
import io.a2a.client.TaskEvent;
import io.a2a.client.TaskUpdateEvent;
import io.a2a.client.config.ClientConfig;
import io.a2a.client.transport.jsonrpc.JSONRPCTransport;
import io.a2a.client.transport.jsonrpc.JSONRPCTransportConfig;
import io.a2a.spec.A2AClientError;
import io.a2a.spec.A2AClientException;
import io.a2a.spec.AgentCard;
import io.a2a.spec.Message;
import io.a2a.spec.Part;
import io.a2a.spec.TextPart;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultA2AClientBuilder<T> implements A2AClientBuilder<T>, InternalAgent, InvocationHandler {

    private final ServiceOutputParser serviceOutputParser = new ServiceOutputParser();

    private final Class<T> agentServiceClass;
    private static final Logger LOG = LoggerFactory.getLogger(DefaultA2AClientBuilder.class);

    private final AgentCard agentCard;
    private final Client a2aClient;

    private final String name;
    private String agentId;
    private InternalAgent parent;

    private String[] inputKeys;
    private String outputKey;
    private String contextIdKey;
    private String taskIdKey;
    private boolean async;

    private AgentListener agentListener;

    DefaultA2AClientBuilder(String a2aServerUrl, Class<T> agentServiceClass) {
        this(agentCard(a2aServerUrl), agentServiceClass);
    }

    private DefaultA2AClientBuilder(AgentCard agentCard, Class<T> agentServiceClass) {
        this(agentCard, a2aClient(agentCard), agentServiceClass);
    }

    DefaultA2AClientBuilder(AgentCard agentCard, Client a2aClient, Class<T> agentServiceClass) {
        this.agentCard = agentCard;
        this.a2aClient = a2aClient;
        this.name = agentCard.name();
        this.agentId = this.name;
        this.agentServiceClass = agentServiceClass;
    }

    private static Client a2aClient(AgentCard agentCard) {
        try {
            return Client.builder(agentCard)
                    .clientConfig(new ClientConfig.Builder()
                            .setStreaming(false) // Disabling streaming
                            .build())
                    .withTransport(JSONRPCTransport.class, new JSONRPCTransportConfig())
                    .build();
        } catch (A2AClientException e) {
            throw new RuntimeException(e);
        }
    }

    private static AgentCard agentCard(String a2aServerUrl) {
        try {
            return A2A.getAgentCard(a2aServerUrl);
        } catch (A2AClientError e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public T build() {
        if (agentServiceClass == UntypedAgent.class && inputKeys == null) {
            throw new IllegalArgumentException("Input names must be provided for UntypedAgent.");
        }

        Object agent = Proxy.newProxyInstance(
                agentServiceClass.getClassLoader(), new Class<?>[] {agentServiceClass, A2AClientInstance.class}, this);

        return (T) agent;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Exception {
        if (method.getDeclaringClass() == AgentInstance.class || method.getDeclaringClass() == InternalAgent.class) {
            return method.invoke(Proxy.getInvocationHandler(proxy), args);
        }

        if (method.getDeclaringClass() == A2AClientInstance.class) {
            return switch (method.getName()) {
                case "agentCard" -> agentCard;
                case "inputKeys" -> inputKeys;
                case "contextIdKey" -> contextIdKey;
                case "taskIdKey" -> taskIdKey;
                default ->
                    throw new UnsupportedOperationException(
                            "Unknown method on A2AClientInstance class : " + method.getName());
            };
        }

        return invokeAgent(method, getReturnType(method), args);
    }

    private static Type getReturnType(Method method) {
        Type type = method.getGenericReturnType();
        return type == Object.class ? String.class : type;
    }

    private Object invokeAgent(Method method, Type returnType, Object[] args) throws A2AClientException {
        List<Part<?>> parts = new ArrayList<>();
        String contextId = null;
        String taskId = null;

        if (agentServiceClass == UntypedAgent.class) {
            Map<String, Object> params = (Map<String, Object>) args[0];
            contextId = valueAsString(readMap(params, contextIdKey));
            taskId = valueAsString(readMap(params, taskIdKey));
            for (String inputKey : inputKeys) {
                if (isA2AMessageContextKey(inputKey)) {
                    continue;
                }
                parts.add(new TextPart(params.get(inputKey).toString()));
            }
        } else {
            Parameter[] parameters = method.getParameters();
            for (int i = 0; i < args.length; i++) {
                Object arg = args[i];
                if (isContextIdParameter(parameters[i])) {
                    contextId = valueAsString(arg);
                    continue;
                }
                if (isTaskIdParameter(parameters[i])) {
                    taskId = valueAsString(arg);
                    continue;
                }
                parts.add(new TextPart(arg.toString()));
            }
        }

        AgenticScope agenticScope = LangChain4jManaged.current(AgenticScope.class);
        if (contextId == null) {
            contextId = valueAsString(readState(agenticScope, contextIdKey));
        }
        if (taskId == null) {
            taskId = valueAsString(readState(agenticScope, taskIdKey));
        }

        Message message = new Message.Builder()
                .role(Message.Role.USER)
                .parts(parts)
                .contextId(contextId)
                .taskId(taskId)
                .build();

        final CompletableFuture<String> messageResponse = new CompletableFuture<>();
        List<BiConsumer<ClientEvent, AgentCard>> consumers = List.of((event, card) -> {
            if (event instanceof MessageEvent messageEvent) {
                messageResponse.complete(messageEvent.getMessage().getParts().stream()
                        .filter(TextPart.class::isInstance)
                        .map(TextPart.class::cast)
                        .map(TextPart::getText)
                        .collect(Collectors.joining("\n")));
            } else if (event instanceof TaskEvent taskEvent) {
                messageResponse.complete(taskEvent.getTask().getArtifacts().stream()
                        .flatMap(a -> a.parts().stream())
                        .filter(TextPart.class::isInstance)
                        .map(TextPart.class::cast)
                        .map(TextPart::getText)
                        .collect(Collectors.joining("\n")));
            } else if (event instanceof TaskUpdateEvent updateEvent) {
                if (updateEvent.getTask().getArtifacts() != null) {
                    messageResponse.complete(updateEvent.getTask().getArtifacts().stream()
                            .flatMap(a -> a.parts().stream())
                            .filter(TextPart.class::isInstance)
                            .map(TextPart.class::cast)
                            .map(TextPart::getText)
                            .collect(Collectors.joining("\n")));
                }
            } else {
                messageResponse.completeExceptionally(
                        new IllegalArgumentException("The event expected should be of type " + event.getClass()));
            }
        });
        // Create error handler for streaming errors
        Consumer<Throwable> streamingErrorHandler = (error) -> {
            LOG.error("Streaming error occurred: " + error.getMessage(), error);
            messageResponse.completeExceptionally(error);
        };
        a2aClient.sendMessage(message, consumers, streamingErrorHandler);
        try {
            String responseText = messageResponse.get();
            LOG.debug("Response: " + responseText);
            return serviceOutputParser.parseText(returnType, responseText);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.error("Failed to get response: " + e.getMessage(), e);
            throw new RuntimeException("Failed to get response: " + e.getMessage(), e);
        } catch (ExecutionException e) {
            LOG.error("Failed to get response: " + e.getMessage(), e);
            throw new RuntimeException("Failed to get response: " + e.getMessage(), e);
        }
    }

    @Override
    public DefaultA2AClientBuilder<T> inputKeys(String... inputKeys) {
        this.inputKeys = inputKeys;
        return this;
    }

    @Override
    public DefaultA2AClientBuilder<T> outputKey(String outputKey) {
        this.outputKey = outputKey;
        return this;
    }

    @Override
    public DefaultA2AClientBuilder<T> contextIdKey(String contextIdKey) {
        this.contextIdKey = contextIdKey;
        return this;
    }

    @Override
    public DefaultA2AClientBuilder<T> taskIdKey(String taskIdKey) {
        this.taskIdKey = taskIdKey;
        return this;
    }

    @Override
    public DefaultA2AClientBuilder<T> async(boolean async) {
        this.async = async;
        return this;
    }

    @Override
    public DefaultA2AClientBuilder<T> listener(AgentListener agentListener) {
        this.agentListener = agentListener;
        return this;
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
        return agentCard.description();
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
        return AgenticSystemTopology.AI_AGENT;
    }

    private boolean isA2AMessageContextKey(String key) {
        return isConfiguredKey(contextIdKey, key) || isConfiguredKey(taskIdKey, key);
    }

    private boolean isContextIdParameter(Parameter parameter) {
        return parameter.isAnnotationPresent(A2AContextId.class) || isConfiguredKey(contextIdKey, parameter);
    }

    private boolean isTaskIdParameter(Parameter parameter) {
        return parameter.isAnnotationPresent(A2ATaskId.class) || isConfiguredKey(taskIdKey, parameter);
    }

    private static boolean isConfiguredKey(String configuredKey, String key) {
        return configuredKey != null && !configuredKey.isBlank() && configuredKey.equals(key);
    }

    private static boolean isConfiguredKey(String configuredKey, Parameter parameter) {
        return isConfiguredKey(
                configuredKey, AgentInvoker.optionalParameterName(parameter).orElse(null));
    }

    private static Object readMap(Map<String, Object> map, String key) {
        return key != null && !key.isBlank() ? map.get(key) : null;
    }

    private static Object readState(AgenticScope agenticScope, String key) {
        return agenticScope != null && key != null && !key.isBlank() ? agenticScope.readState(key) : null;
    }

    private static String valueAsString(Object value) {
        return value == null ? null : value.toString();
    }
}
