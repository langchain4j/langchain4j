package dev.langchain4j.agentic.a2a;

import static dev.langchain4j.agentic.observability.ComposedAgentListener.composeWithInherited;

import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.internal.A2AClientBuilder;
import dev.langchain4j.agentic.internal.InternalAgent;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.planner.AgentArgument;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.invocation.LangChain4jManaged;
import dev.langchain4j.service.ParameterNameResolver;
import dev.langchain4j.service.output.ServiceOutputParser;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.a2aproject.sdk.A2A;
import org.a2aproject.sdk.client.Client;
import org.a2aproject.sdk.client.ClientBuilder;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultA2AClientBuilder<T> implements A2AClientBuilder<T>, InternalAgent, InvocationHandler {

    private record A2AInvocationResult(
            Object parsedResult, String contextIdKey, String contextId, String taskIdKey, String taskId) {}

    private final ServiceOutputParser serviceOutputParser = new ServiceOutputParser();

    private final Class<T> agentServiceClass;
    private static final Logger LOG = LoggerFactory.getLogger(DefaultA2AClientBuilder.class);

    private final AgentCard agentCard;
    private Client a2aClient;
    private Consumer<ClientBuilder> clientCustomizer;

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
        this.agentServiceClass = agentServiceClass;
    }

    private Client buildClient() {
        try {
            ClientBuilder cb = Client.builder(agentCard);
            if (clientCustomizer != null) {
                clientCustomizer.accept(cb);
            } else {
                cb.withTransport(JSONRPCTransport.class, new JSONRPCTransportConfigBuilder());
            }
            return cb.build();
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

        this.a2aClient = buildClient();

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
                default ->
                    throw new UnsupportedOperationException(
                            "Unknown method on A2AClientInstance class : " + method.getName());
            };
        }

        boolean wrapWithScope = method.getReturnType() == ResultWithAgenticScope.class;
        Type returnType = wrapWithScope ? unwrapResultType(method.getGenericReturnType()) : getReturnType(method);

        A2AInvocationResult result = invokeAgent(method, returnType, args);

        AgenticScope scope = LangChain4jManaged.current(AgenticScope.class);
        if (scope == null) {
            scope = DefaultAgenticScope.ephemeralAgenticScope();
            if (outputKey != null && result.parsedResult != null) {
                scope.writeState(outputKey, result.parsedResult);
            }
        }
        if (result.contextIdKey != null && result.contextId != null) {
            scope.writeState(result.contextIdKey, result.contextId);
        }
        if (result.taskIdKey != null && result.taskId != null) {
            scope.writeState(result.taskIdKey, result.taskId);
        }

        return method.getReturnType() == ResultWithAgenticScope.class
                ? new ResultWithAgenticScope<>(scope, result.parsedResult)
                : result.parsedResult;
    }

    private static Type getReturnType(Method method) {
        Type type = method.getGenericReturnType();
        return type == Object.class ? String.class : type;
    }

    private static Type unwrapResultType(Type type) {
        if (type instanceof ParameterizedType pt && pt.getRawType() == ResultWithAgenticScope.class) {
            Type inner = pt.getActualTypeArguments()[0];
            return inner == Object.class ? String.class : inner;
        }
        return String.class;
    }

    private A2AInvocationResult invokeAgent(Method method, Type returnType, Object[] args) throws A2AClientException {
        List<Part<?>> parts = new ArrayList<>();
        String contextId = null;
        String taskId = null;
        String contextIdKey = null;
        String taskIdKey = null;

        if (agentServiceClass == UntypedAgent.class) {
            Map<String, Object> params = (Map<String, Object>) args[0];
            for (String inputKey : inputKeys) {
                parts.add(new TextPart(params.get(inputKey).toString()));
            }
        } else {
            java.lang.reflect.Parameter[] parameters = method.getParameters();
            for (int i = 0; i < args.length; i++) {
                if (parameters[i].getAnnotation(A2AContextId.class) != null) {
                    contextId = args[i] != null ? args[i].toString() : null;
                    if (ParameterNameResolver.hasName(parameters[i])) {
                        contextIdKey = ParameterNameResolver.name(parameters[i]);
                    }
                } else if (parameters[i].getAnnotation(A2ATaskId.class) != null) {
                    taskId = args[i] != null ? args[i].toString() : null;
                    if (ParameterNameResolver.hasName(parameters[i])) {
                        taskIdKey = ParameterNameResolver.name(parameters[i]);
                    }
                } else {
                    parts.add(new TextPart(args[i].toString()));
                }
            }
        }

        Message.Builder messageBuilder =
                Message.builder().role(Message.Role.ROLE_USER).parts(parts);
        if (contextId != null) {
            messageBuilder.contextId(contextId);
        }
        if (taskId != null) {
            messageBuilder.taskId(taskId);
        }
        Message message = messageBuilder.build();

        final CompletableFuture<String> messageResponse = new CompletableFuture<>();
        AtomicReference<String> responseContextId = new AtomicReference<>();
        AtomicReference<String> responseTaskId = new AtomicReference<>();

        List<BiConsumer<ClientEvent, AgentCard>> consumers = List.of((event, card) -> {
            if (event instanceof MessageEvent messageEvent) {
                Message msg = messageEvent.getMessage();
                responseContextId.set(msg.contextId());
                responseTaskId.set(msg.taskId());
                messageResponse.complete(extractTextFromParts(msg.parts()));
            } else if (event instanceof TaskEvent taskEvent) {
                captureTaskIds(taskEvent.getTask(), responseContextId, responseTaskId);
                completeFromTask(taskEvent.getTask(), messageResponse);
            } else if (event instanceof TaskUpdateEvent updateEvent) {
                captureTaskIds(updateEvent.getTask(), responseContextId, responseTaskId);
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

        String finalContextIdKey = contextIdKey;
        String finalTaskIdKey = taskIdKey;
        try {
            String responseText = messageResponse.get();
            LOG.debug("Response: {}", responseText);
            Object parsedResult = serviceOutputParser.parseText(returnType, responseText);
            return new A2AInvocationResult(
                    parsedResult, finalContextIdKey, responseContextId.get(), finalTaskIdKey, responseTaskId.get());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.error("Failed to get response: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to get response: " + e.getMessage(), e);
        } catch (ExecutionException e) {
            LOG.error("Failed to get response: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to get response: " + e.getMessage(), e);
        }
    }

    private static void captureTaskIds(Task task, AtomicReference<String> contextId, AtomicReference<String> taskId) {
        contextId.set(task.contextId());
        taskId.set(task.id());
    }

    static void completeFromTask(Task task, CompletableFuture<String> messageResponse) {
        TaskState state = task.status().state();
        if (!isTerminalState(state) && task.artifacts().isEmpty()) {
            return;
        }
        if (isFailureState(state)) {
            Message statusMessage = task.status().message();
            String reason = statusMessage != null ? extractTextFromParts(statusMessage.parts()) : "";
            messageResponse.completeExceptionally(new RuntimeException("A2A task " + task.id()
                    + " ended in terminal state " + state + (reason.isEmpty() ? "" : ": " + reason)));
            return;
        }
        messageResponse.complete(extractTextFromParts(
                task.artifacts().stream().flatMap(a -> a.parts().stream()).toList()));
    }

    private static boolean isFailureState(TaskState state) {
        return state == TaskState.TASK_STATE_FAILED
                || state == TaskState.TASK_STATE_CANCELED
                || state == TaskState.TASK_STATE_REJECTED;
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
    @SuppressWarnings("unchecked")
    public DefaultA2AClientBuilder<T> clientCustomizer(Consumer<?> clientCustomizer) {
        if (clientCustomizer != null) {
            this.clientCustomizer = (Consumer<ClientBuilder>) clientCustomizer;
        }
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
        return agentServiceClass;
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
