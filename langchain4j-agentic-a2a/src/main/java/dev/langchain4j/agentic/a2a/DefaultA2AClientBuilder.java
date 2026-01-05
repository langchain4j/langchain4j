package dev.langchain4j.agentic.a2a;

import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.internal.InternalAgent;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentListenerProvider;
import dev.langchain4j.agentic.internal.A2AClientBuilder;
import dev.langchain4j.agentic.planner.AgentArgument;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
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
    private AgentInstance parent;

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
                    .clientConfig(new ClientConfig.Builder()
                            .setStreaming(false) // Disabling streaming
                            .build())
                    .withTransport(JSONRPCTransport.class, new JSONRPCTransportConfig())
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
                new Class<?>[] {agentServiceClass, A2AClientInstance.class, AgentListenerProvider.class}, this);

        return (T) agent;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Exception {
        if (method.getDeclaringClass() == AgentInstance.class || method.getDeclaringClass() == InternalAgent.class) {
            return method.invoke(Proxy.getInvocationHandler(proxy), args);
        }

        if (method.getDeclaringClass() == AgentListenerProvider.class) {
            return agentListener;
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
                new Message.Builder().role(Message.Role.USER).parts(parts).build();

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
    public void setParent(AgentInstance parent) {
        this.parent = parent;
    }

    @Override
    public void appendId(String idSuffix) {
        this.agentId = this.agentId + idSuffix;
    }

    @Override
    public Class<?> type() {
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
        return AgenticSystemTopology.SINGLE_AGENT;
    }
}
