package dev.langchain4j.agentic.a2a;

import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.internal.InternalAgent;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.internal.A2AClientBuilder;
import dev.langchain4j.agentic.planner.AgentArgument;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.service.output.ServiceOutputParser;
import org.a2aproject.sdk.A2A;
import org.a2aproject.sdk.client.Client;
import org.a2aproject.sdk.client.ClientEvent;
import org.a2aproject.sdk.client.MessageEvent;
import org.a2aproject.sdk.client.TaskEvent;
import org.a2aproject.sdk.client.TaskUpdateEvent;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransport;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransportConfigBuilder;
import org.a2aproject.sdk.spec.A2AClientError;
import org.a2aproject.sdk.spec.A2AClientException;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TextPart;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
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

import static dev.langchain4j.agentic.observability.ComposedAgentListener.composeWithInherited;

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
    private boolean async;

    private AgentListener agentListener;

    DefaultA2AClientBuilder(String a2aServerUrl, Class<T> agentServiceClass) {
        this.agentCard = agentCard(a2aServerUrl);
        this.name = agentCard.name();
        this.agentId = this.name;
        try {
            this.a2aClient = Client.builder(agentCard)
                    .withTransport(JSONRPCTransport.class, new JSONRPCTransportConfigBuilder())
                    .build();
        } catch (A2AClientException e) {
            throw new RuntimeException(e);
        }
        this.agentServiceClass = agentServiceClass;
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
                agentServiceClass.getClassLoader(),
                new Class<?>[] {agentServiceClass, A2AClientInstance.class}, this);

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
                default ->
                        throw new UnsupportedOperationException(
                                "Unknown method on A2AClientInstance class : " + method.getName());
            };
        }

        return invokeAgent(getReturnType(method), args);
    }

    private static Type getReturnType(Method method) {
        Type type = method.getGenericReturnType();
        return type == Object.class ? String.class : type;
    }

    private Object invokeAgent(Type returnType, Object[] args) throws A2AClientException {
        List<Part<?>> parts = new ArrayList<>();

        if (agentServiceClass == UntypedAgent.class) {
            Map<String, Object> params = (Map<String, Object>) args[0];
            for (String inputKey : inputKeys) {
                parts.add(new TextPart(params.get(inputKey).toString()));
            }
        } else {
            for (Object arg : args) {
                parts.add(new TextPart(arg.toString()));
            }
        }

        Message message =
                Message.builder().role(Message.Role.ROLE_USER).parts(parts).build();

        final CompletableFuture<String> messageResponse = new CompletableFuture<>();
        List<BiConsumer<ClientEvent, AgentCard>> consumers = List.of((event, card) -> {
            if (event instanceof MessageEvent messageEvent) {
                messageResponse.complete(extractTextFromParts(messageEvent.getMessage().parts()));
            } else if (event instanceof TaskEvent taskEvent) {
                completeFromTask(taskEvent.getTask(), messageResponse);
            } else if (event instanceof TaskUpdateEvent updateEvent) {
                completeFromTask(updateEvent.getTask(), messageResponse);
            } else {
                messageResponse.completeExceptionally(
                        new IllegalArgumentException("The event expected should be of type " + event.getClass()));
            }
        });
        Consumer<Throwable> streamingErrorHandler = error -> {
            if (messageResponse.isDone()) {
                LOG.debug("SSE stream closed after response received: {}", error.getMessage());
            } else {
                LOG.error("Streaming error occurred: {}", error.getMessage(), error);
                messageResponse.completeExceptionally(error);
            }
        };
        a2aClient.sendMessage(message, consumers, streamingErrorHandler, null);
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

    private static void completeFromTask(Task task, CompletableFuture<String> messageResponse) {
        if (!isTerminalState(task.status().state()) && task.artifacts().isEmpty()) {
            return;
        }
        messageResponse.complete(extractTextFromParts(
                task.artifacts().stream().flatMap(a -> a.parts().stream()).toList()));
    }

    private static boolean isTerminalState(TaskState state) {
        return state == TaskState.TASK_STATE_COMPLETED
                || state == TaskState.TASK_STATE_FAILED
                || state == TaskState.TASK_STATE_CANCELED
                || state == TaskState.TASK_STATE_REJECTED;
    }

    private static String extractTextFromParts(List<Part<?>> parts) {
        return parts.stream()
                .filter(TextPart.class::isInstance)
                .map(TextPart.class::cast)
                .map(TextPart::text)
                .collect(Collectors.joining("\n"));
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
}
