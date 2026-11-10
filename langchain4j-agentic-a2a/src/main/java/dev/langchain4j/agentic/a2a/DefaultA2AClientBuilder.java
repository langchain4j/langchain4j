package dev.langchain4j.agentic.a2a;

import static dev.langchain4j.agentic.observability.ComposedAgentListener.compose;
import static dev.langchain4j.agentic.observability.ComposedAgentListener.composeWithInherited;
import static dev.langchain4j.internal.Utils.isNullOrBlank;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.agent.MissingArgumentException;
import dev.langchain4j.agentic.declarative.A2AClientAgent;
import dev.langchain4j.agentic.internal.A2AClientBuilder;
import dev.langchain4j.agentic.internal.AgentUtil;
import dev.langchain4j.agentic.internal.InternalAgent;
import dev.langchain4j.agentic.internal.SuspendedResponse;
import dev.langchain4j.agentic.observability.A2AStreamingClientListener;
import dev.langchain4j.agentic.observability.A2AStreamingClientListenerResult;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.planner.AgentArgument;
import dev.langchain4j.agentic.planner.AgentCondition;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgenticSystemConfigurationException;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.AgenticSystemSuspendedException;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.invocation.LangChain4jManaged;
import dev.langchain4j.service.ParameterNameResolver;
import dev.langchain4j.service.output.ServiceOutputParser;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.a2aproject.sdk.client.Client;
import org.a2aproject.sdk.client.ClientBuilder;
import org.a2aproject.sdk.client.ClientEvent;
import org.a2aproject.sdk.client.MessageEvent;
import org.a2aproject.sdk.client.TaskEvent;
import org.a2aproject.sdk.client.TaskUpdateEvent;
import org.a2aproject.sdk.client.http.A2ACardResolver;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransport;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransportConfigBuilder;
import org.a2aproject.sdk.spec.A2AClientError;
import org.a2aproject.sdk.spec.A2AClientException;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.MessageSendParams;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TextPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultA2AClientBuilder<T> implements A2AClientBuilder<T>, InternalAgent, InvocationHandler {

    static final String INTERRUPTION_STATE_PREFIX = "a2a.interruption.";
    private static final String RESPONSE_STATE_PREFIX = "a2a.response.";

    private record A2AInvocationResult(
            Object parsedResult, String contextIdKey, String contextId, String taskIdKey, String openTaskId) {}

    private final ServiceOutputParser serviceOutputParser = new ServiceOutputParser();

    private final Class<T> agentServiceClass;
    private static final Logger LOG = LoggerFactory.getLogger(DefaultA2AClientBuilder.class);

    private final String a2aServerUrl;
    private Supplier<AgentCard> agentCardSource;
    private final Function<AgentCard, Client> clientFactory;
    private final ReentrantLock lazyInitLock = new ReentrantLock();

    // The agent card is fetched lazily, the first time it is needed, and the client is created from it at that
    // point, so that building an agentic system never depends on the availability of the remote agent
    private volatile AgentCard agentCard;
    private volatile Client a2aClient;
    private Consumer<ClientBuilder> clientCustomizer;

    private String name;
    private String agentId;
    private String description;
    private InternalAgent parent;
    private String tenant;

    private String[] inputKeys;
    private String outputKey;
    private boolean async;

    private AgentListener agentListener;
    private A2AStreamingClientListener<TaskUpdateEvent> streamingClientListener;
    private AgentCondition activationCondition;

    DefaultA2AClientBuilder(String a2aServerUrl, Class<T> agentServiceClass) {
        this(a2aServerUrl, agentServiceClass, null);
    }

    DefaultA2AClientBuilder(String a2aServerUrl, Class<T> agentServiceClass, String tenant) {
        this.a2aServerUrl = a2aServerUrl;
        this.agentServiceClass = agentServiceClass;
        this.tenant = (tenant != null && !tenant.isEmpty()) ? tenant : null;
        // The URL is validated now, so that a configuration error still fails when the agent is built,
        // but the card itself is fetched only when first needed
        A2ACardResolver cardResolver = cardResolver(a2aServerUrl, this.tenant);
        this.agentCardSource = cardResolver::getAgentCard;
        this.clientFactory = this::buildClient;
    }

    // For testing only: bypasses URL fetch and pre-sets the client.
    DefaultA2AClientBuilder(AgentCard agentCard, Class<T> agentServiceClass, Client a2aClient) {
        this.a2aServerUrl = agentCard.url();
        this.agentServiceClass = agentServiceClass;
        this.agentCardSource = () -> agentCard;
        this.clientFactory = card -> a2aClient;
        this.agentCard = agentCard;
        this.a2aClient = a2aClient;
    }

    // For testing only: the card comes lazily from the given source and the client from the given factory,
    // or from the card itself when the factory is null.
    DefaultA2AClientBuilder(
            String a2aServerUrl,
            Supplier<AgentCard> agentCardSource,
            Class<T> agentServiceClass,
            Function<AgentCard, Client> clientFactory) {
        this.a2aServerUrl = a2aServerUrl;
        this.agentServiceClass = agentServiceClass;
        this.agentCardSource = agentCardSource;
        this.clientFactory = clientFactory != null ? clientFactory : this::buildClient;
    }

    private static A2ACardResolver cardResolver(String a2aServerUrl, String tenant) {
        A2ACardResolver cardResolver;
        try {
            cardResolver = A2ACardResolver.builder()
                    .baseUrl(a2aServerUrl)
                    .agentCardPath(null)
                    .tenant(tenant)
                    .authHeaders(null)
                    .build();
        } catch (A2AClientError | IllegalArgumentException e) {
            String invalidValue = "A2A server URL '" + a2aServerUrl + "'" + (tenant != null ? " or tenant '" + tenant + "'" : "");
            throw new AgenticSystemConfigurationException("Invalid " + invalidValue + ": " + e.getMessage(), e);
        }
        URI uri = URI.create(a2aServerUrl);
        boolean httpScheme = "http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme());
        if (!httpScheme || uri.getHost() == null) {
            throw new AgenticSystemConfigurationException("Invalid A2A server URL '" + a2aServerUrl
                    + "': it must be an absolute http or https URL, like http://localhost:8080");
        }
        return cardResolver;
    }

    /**
     * Returns the agent card, fetching it from the A2A server the first time. Concurrent first invocations perform
     * a single fetch, and a failed fetch is not cached, so that the next invocation retries it.
     */
    private AgentCard resolveAgentCard() {
        AgentCard card = agentCard;
        if (card == null) {
            lockForLazyInit();
            try {
                card = agentCard;
                if (card == null) {
                    card = fetchAgentCard();
                    agentCard = card;
                    agentCardSource = null;
                }
            } finally {
                lazyInitLock.unlock();
            }
        }
        return card;
    }

    private void lockForLazyInit() {
        try {
            lazyInitLock.lockInterruptibly();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new A2AAgentCardException(name(), a2aServerUrl, e);
        }
    }

    private AgentCard fetchAgentCard() {
        try {
            return agentCardSource.get();
        } catch (RuntimeException e) {
            throw new A2AAgentCardException(name(), a2aServerUrl, e);
        }
    }

    private Client client() {
        Client client = a2aClient;
        if (client == null) {
            lockForLazyInit();
            try {
                client = a2aClient;
                if (client == null) {
                    client = clientFactory.apply(resolveAgentCard());
                    a2aClient = client;
                }
            } finally {
                lazyInitLock.unlock();
            }
        }
        return client;
    }

    private Client buildClient(AgentCard card) {
        try {
            ClientBuilder cb = Client.builder(card);
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

    @Override
    public T build() {
        if (agentServiceClass == UntypedAgent.class && inputKeys == null) {
            throw new IllegalArgumentException("Input names must be provided for UntypedAgent.");
        }

        if (description == null) {
            description = agentMethod().map(DefaultA2AClientBuilder::annotatedDescription).orElse(null);
        }
        // The local name, and the agent id derived from it, are fixed before the proxy is handed out
        agentId();

        Object agent = Proxy.newProxyInstance(
                agentServiceClass.getClassLoader(), new Class<?>[] {agentServiceClass, A2AClientInstance.class}, this);

        return (T) agent;
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

        if (method.getDeclaringClass() == A2AClientInstance.class) {
            return switch (method.getName()) {
                case "agentCard" -> resolveAgentCard();
                case "inputKeys" -> inputKeys;
                case "tenant" -> configuredTenant();
                default ->
                    throw new UnsupportedOperationException(
                            "Unknown method on A2AClientInstance class : " + method.getName());
            };
        }

        boolean wrapWithScope = method.getReturnType() == ResultWithAgenticScope.class;
        Type returnType = wrapWithScope ? unwrapResultType(method.getGenericReturnType()) : getReturnType(method);

        AgenticScope scope = LangChain4jManaged.current(AgenticScope.class);
        A2AInvocationResult result = invokeAgent(method, returnType, args, scope);

        if (scope == null) {
            scope = DefaultAgenticScope.ephemeralAgenticScope();
            if (outputKey != null && result.parsedResult != null) {
                scope.writeState(outputKey, result.parsedResult);
            }
        }
        if (result.contextIdKey != null && result.contextId != null) {
            scope.writeState(result.contextIdKey, result.contextId);
        }
        if (result.taskIdKey != null) {
            // The task id is propagated only while the remote task is still open: a task that already
            // reached a terminal state cannot accept further messages, so its id must not survive in
            // the scope for the next invocation
            scope.writeState(result.taskIdKey, result.openTaskId);
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

    private A2AInvocationResult invokeAgent(Method method, Type returnType, Object[] args, AgenticScope scope)
            throws A2AClientException {
        List<Part<?>> parts = new ArrayList<>();
        String contextId = null;
        String taskId = null;
        // Per-call tenant extracted from the @A2ATenantId-annotated parameter at invocation time.
        // Falls back to the instance-level tenant (from @A2AClientAgent.tenant() or the agent card URL).
        String callTenant = null;
        String contextIdKey = null;
        String taskIdKey = null;

        if (agentServiceClass == UntypedAgent.class) {
            Map<String, Object> params = (Map<String, Object>) args[0];
            for (String inputKey : inputKeys) {
                Object value = params.get(inputKey);
                if (value == null) {
                    throw new MissingArgumentException(inputKey);
                }
                parts.add(new TextPart(value.toString()));
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
                } else if (parameters[i].getAnnotation(A2ATenantId.class) != null) {
                    // @A2ATenantId parameter overrides the instance-level tenant only if non-empty
                    if (args[i] != null && !args[i].toString().isEmpty()) {
                        callTenant = args[i].toString();
                    }
                } else {
                    parts.add(new TextPart(args[i].toString()));
                }
            }
        }

        if (callTenant == null) {
            callTenant = effectiveTenant();
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

        String interruptionKey = INTERRUPTION_STATE_PREFIX + agentId();
        String responseKey = RESPONSE_STATE_PREFIX + agentId();
        A2AResponse response;
        if (scope != null && scope.state().get(responseKey) instanceof String input) {
            A2ATaskInterruptedException interruption =
                    A2ATaskInterruptedException.fromMetadata(scope.state().get(interruptionKey));
            response = sendMessage(continuationMessage(interruption, input), callTenant);
        } else {
            response = sendMessage(message, callTenant);
        }

        if (response.interruption != null) {
            if (scope != null) {
                suspend(scope, agentId(), response.interruption);
            }
            throw response.interruption;
        }

        if (scope != null) {
            scope.writeState(interruptionKey, null);
            scope.writeState(responseKey, null);
        }

        LOG.debug("Response: {}", response.text);
        Object parsedResult = serviceOutputParser.parseText(returnType, response.text);
        return new A2AInvocationResult(parsedResult, contextIdKey, response.contextId, taskIdKey, response.openTaskId);
    }

    /**
     * @param openTaskId the id of the remote task if it was still running when this response was
     *     produced, {@code null} if the task reached a terminal state or no task was created.
     */
    private record A2AResponse(
            String text, String contextId, String openTaskId, A2ATaskInterruptedException interruption) {}

    static void suspend(AgenticScope scope, String agentId, A2ATaskInterruptedException interruption) {
        String responseId = agentId + ":" + interruption.taskId();
        scope.writeState(INTERRUPTION_STATE_PREFIX + agentId, interruptionMetadata(responseId, interruption));
        scope.writeState(RESPONSE_STATE_PREFIX + agentId, new SuspendedResponse<>(responseId));
        throw new AgenticSystemSuspendedException(scope);
    }

    private static Map<String, String> interruptionMetadata(
            String responseId, A2ATaskInterruptedException interruption) {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("responseId", responseId);
        metadata.put("taskId", interruption.taskId());
        metadata.put("contextId", interruption.contextId());
        metadata.put("state", interruption.state().name());
        if (interruption.reason() != null) {
            metadata.put("reason", interruption.reason());
        }
        return metadata;
    }

    static Message continuationMessage(A2ATaskInterruptedException interruption, String input) {
        return Message.builder()
                .role(Message.Role.ROLE_USER)
                .parts(List.of(new TextPart(Objects.requireNonNull(input, "A2A response must not be null"))))
                .contextId(interruption.contextId())
                .taskId(interruption.taskId())
                .build();
    }

    private A2AResponse sendMessage(Message message, String callTenant) throws A2AClientException {
        final CompletableFuture<String> messageResponse = new CompletableFuture<>();
        AtomicReference<String> responseContextId = new AtomicReference<>();
        AtomicReference<String> openTaskId = new AtomicReference<>();
        List<BiConsumer<ClientEvent, AgentCard>> consumers =
                getEventConsumers(responseContextId, openTaskId, messageResponse);
        Consumer<Throwable> streamingErrorHandler = error -> handleStreamEnd(error, messageResponse);
        Client client = client();
        if (callTenant != null) {
            client.sendMessage(
                    MessageSendParams.builder()
                            .message(message)
                            .tenant(callTenant)
                            .build(),
                    consumers,
                    streamingErrorHandler,
                    null);
        } else {
            client.sendMessage(message, consumers, streamingErrorHandler, null);
        }

        try {
            String responseText = messageResponse.get();
            return new A2AResponse(responseText, responseContextId.get(), openTaskId.get(), null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.error("Failed to get response: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to get response: " + e.getMessage(), e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof A2ATaskInterruptedException interruption) {
                return new A2AResponse(null, interruption.contextId(), null, interruption);
            }
            LOG.error("Failed to get response: {}", e.getMessage(), e);
            if (e.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException("Failed to get response: " + e.getMessage(), e);
        }
    }

    private List<BiConsumer<ClientEvent, AgentCard>> getEventConsumers(
            AtomicReference<String> responseContextId,
            AtomicReference<String> openTaskId,
            CompletableFuture<String> messageResponse) {

        AtomicBoolean stopped = new AtomicBoolean(false);

        BiConsumer<ClientEvent, AgentCard> defaultEventConsumer = (event, card) -> {
            if (stopped.get()) {
                return;
            }

            if (event instanceof TaskEvent taskEvent) {
                responseContextId.set(taskEvent.getTask().contextId());
                handleTaskEvent(taskEvent, messageResponse);
            } else if (event instanceof MessageEvent messageEvent) {
                Message msg = messageEvent.getMessage();
                responseContextId.set(msg.contextId());
                handleMessageEvent(msg, messageResponse);
            } else if (event instanceof TaskUpdateEvent updateEvent) {
                responseContextId.set(updateEvent.getTask().contextId());
                handleUpdateEvent(updateEvent, openTaskId, messageResponse, stopped);
            } else {
                messageResponse.completeExceptionally(
                        new IllegalArgumentException("The event expected should be of type " + event.getClass()));
            }
        };

        return List.of(defaultEventConsumer);
    }

    static void handleStreamEnd(Throwable error, CompletableFuture<String> messageResponse) {
        if (error == null) {
            // The A2A SDK reports a normal end of the stream by passing a null error.
            LOG.debug("SSE stream closed normally");
            if (!messageResponse.isDone()) {
                messageResponse.completeExceptionally(
                        new RuntimeException("A2A stream closed before a result was received"));
            }
            return;
        }
        if (messageResponse.isDone()) {
            LOG.debug("SSE stream closed after response received: {}", error.getMessage());
        } else {
            LOG.error("Streaming error occurred: {}", error.getMessage(), error);
            messageResponse.completeExceptionally(error);
        }
    }

    private void handleTaskEvent(TaskEvent taskEvent, CompletableFuture<String> messageResponse) {
        // The agent MAY create a new Task to process the provided message asynchronously or MAY return
        // a direct Message response for simple interactions. The operation MUST return immediately with
        // either task information or response message. Task processing MAY continue asynchronously after
        // the response when a Task is returned.
        completeFromTask(taskEvent.getTask(), messageResponse);
    }

    private void handleMessageEvent(Message message, CompletableFuture<String> messageResponse) {
        // Message-only stream: If the agent returns a Message, the stream MUST contain exactly one
        // Message object and then close immediately. No task tracking or updates are provided.
        messageResponse.complete(extractTextFromParts(message.parts()));
    }

    private void handleUpdateEvent(
            TaskUpdateEvent taskUpdateEvent,
            AtomicReference<String> openTaskId,
            CompletableFuture<String> messageResponse,
            AtomicBoolean stopped) {
        // Task lifecycle stream: If the agent returns a Task, the stream MUST begin with the Task
        // object, followed by zero or more TaskStatusUpdateEvent or TaskArtifactUpdateEvent objects.
        // The stream MUST close when the task reaches a terminal state
        // (TASK_STATE_COMPLETED, TASK_STATE_FAILED, TASK_STATE_CANCELED, TASK_STATE_REJECTED).
        if (streamingClientListener != null) {
            A2AStreamingClientListenerResult listenerResult = streamingClientListener.onUpdateEvent(taskUpdateEvent);
            if (listenerResult.stop()) {
                Task task = taskUpdateEvent.getTask();
                if (listenerResult.withCurrentArtifacts()) {
                    completeArtifact(task.artifacts(), messageResponse);
                } else {
                    String response = listenerResult.response();
                    messageResponse.complete(response == null ? "" : response);
                }
                if (!task.status().state().isFinal()) {
                    // Stopping the client-side stream does not cancel the remote task: this is the only
                    // path on which an invocation returns normally while the task is still open, so its
                    // id is kept so that the caller can still poll, cancel or continue it.
                    openTaskId.set(task.id());
                }
                stopped.set(true);
                return;
            }
        }
        completeFromTask(taskUpdateEvent.getTask(), messageResponse);
    }

    static void completeFromTask(Task task, CompletableFuture<String> messageResponse) {
        TaskState state = task.status().state();

        if (!state.isInterrupted() && !state.isFinal()) {
            return;
        }

        if (state.isInterrupted()) {
            // The remote agent paused the task (input-required/auth-required) and will not advance it
            // on its own, so this must complete exceptionally rather than being left pending forever.
            Message statusMessage = task.status().message();
            String reason = statusMessage != null ? extractTextFromParts(statusMessage.parts()) : "";
            messageResponse.completeExceptionally(
                    new A2ATaskInterruptedException(task.id(), task.contextId(), state, reason));
            return;
        }
        if (isFailureState(state)) {
            Message statusMessage = task.status().message();
            String reason = statusMessage != null ? extractTextFromParts(statusMessage.parts()) : "";
            messageResponse.completeExceptionally(new RuntimeException("A2A task " + task.id()
                    + " ended in terminal state " + state + (reason.isEmpty() ? "" : ": " + reason)));
            return;
        }
        completeArtifact(task.artifacts(), messageResponse);
    }

    private static void completeArtifact(List<Artifact> artifacts, CompletableFuture<String> messageResponse) {
        if (artifacts == null || artifacts.isEmpty()) {
            messageResponse.complete("");
            return;
        }
        messageResponse.complete(extractTextFromParts(artifacts.stream()
                .flatMap(artifact -> artifact.parts().stream())
                .toList()));
    }

    private static boolean isFailureState(TaskState state) {
        return state == TaskState.TASK_STATE_FAILED
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
    public DefaultA2AClientBuilder<T> name(String name) {
        if (name != null && !name.isBlank()) {
            this.name = name;
        }
        return this;
    }

    @Override
    public DefaultA2AClientBuilder<T> description(String description) {
        if (description != null && !description.isBlank()) {
            this.description = description;
        }
        return this;
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
        this.agentListener = compose(this.agentListener, agentListener);
        return this;
    }

    @Override
    public DefaultA2AClientBuilder<T> activationCondition(
            String description, Predicate<AgenticScope> activationCondition) {
        this.activationCondition = new AgentCondition(description, activationCondition);
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
    @SuppressWarnings("unchecked")
    public A2AClientBuilder<T> streamingClientListener(A2AStreamingClientListener<?> clientListener) {
        this.streamingClientListener = (A2AStreamingClientListener<TaskUpdateEvent>) clientListener;
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
        this.agentId = agentId() + idSuffix;
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
     * The name is always known locally, without fetching the agent card: it is the explicit one, else the one of the
     * annotation of the agent method, else the name of that method for a typed agent, else a placeholder derived
     * from the A2A server URL. It never changes afterwards, not even when the card is fetched, because the agent id
     * is derived from it.
     */
    @Override
    public String name() {
        if (name == null) {
            name = defaultName();
        }
        return name;
    }

    private String defaultName() {
        Optional<Method> agentMethod = agentMethod();
        if (agentMethod.isPresent()) {
            String annotatedName = annotatedName(agentMethod.get());
            return isNullOrBlank(annotatedName) ? agentMethod.get().getName() : annotatedName;
        }
        AgentCard card = agentCard;
        return card != null ? card.name() : placeholderName(a2aServerUrl);
    }

    private Optional<Method> agentMethod() {
        if (agentServiceClass == UntypedAgent.class) {
            return Optional.empty();
        }
        Optional<Method> agentMethod = AgentUtil.getAnnotatedMethodOnClass(agentServiceClass, Agent.class)
                .or(() -> AgentUtil.getAnnotatedMethodOnClass(agentServiceClass, A2AClientAgent.class));
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
        A2AClientAgent a2aClientAgent = agentMethod.getAnnotation(A2AClientAgent.class);
        return a2aClientAgent != null ? a2aClientAgent.name() : null;
    }

    private static String annotatedDescription(Method agentMethod) {
        Agent agent = agentMethod.getAnnotation(Agent.class);
        if (agent != null) {
            return firstNonBlank(agent.description(), agent.value());
        }
        A2AClientAgent a2aClientAgent = agentMethod.getAnnotation(A2AClientAgent.class);
        return a2aClientAgent != null ? firstNonBlank(a2aClientAgent.description(), a2aClientAgent.value()) : null;
    }

    private static String firstNonBlank(String first, String second) {
        if (!isNullOrBlank(first)) {
            return first;
        }
        return isNullOrBlank(second) ? null : second;
    }

    /**
     * A name derived from the host, port and path of the A2A server URL, like {@code a2a-remote-host-8080-agents-fixer}
     * for {@code http://remote-host:8080/agents/fixer}: the same URL always gives the same name, and agents sharing
     * it inside an agentic system are told apart by the index suffix of their agent id.
     */
    static String placeholderName(String a2aServerUrl) {
        if (a2aServerUrl != null) {
            try {
                URI uri = URI.create(a2aServerUrl);
                if (uri.getHost() != null) {
                    StringBuilder placeholder = new StringBuilder("a2a-").append(uri.getHost());
                    if (uri.getPort() >= 0) {
                        placeholder.append('-').append(uri.getPort());
                    }
                    if (uri.getPath() != null) {
                        Stream.of(uri.getPath().split("/"))
                                .filter(segment -> !segment.isEmpty())
                                .forEach(segment -> placeholder.append('-').append(segment));
                    }
                    return placeholder.toString();
                }
            } catch (IllegalArgumentException e) {
                LOG.debug("Cannot derive a name from the A2A server URL {}", a2aServerUrl, e);
            }
        }
        return "a2a-agent";
    }

    @Override
    public String agentId() {
        if (agentId == null) {
            agentId = name();
        }
        return agentId;
    }

    /**
     * The explicit description, else the one of the agent card, which is fetched at this point if not yet done.
     */
    @Override
    public String description() {
        return description != null ? description : resolveAgentCard().description();
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

    @Override
    public DefaultA2AClientBuilder<T> tenant(String tenant) {
        if (tenant != null && !tenant.isEmpty()) {
            this.tenant = tenant;
        }
        return this;
    }

    /**
     * The configured tenant, or the one of the agent card when the card has already been fetched. It never fetches
     * the card, because it is read when the agentic system is built.
     */
    private String configuredTenant() {
        if (tenant != null) {
            return tenant;
        }
        AgentCard card = agentCard;
        return card != null ? extractTenantFromAgentCard(card) : null;
    }

    /**
     * The tenant sent with the messages: the configured one, or the one of the agent card, fetched if needed.
     */
    private String effectiveTenant() {
        return tenant != null ? tenant : extractTenantFromAgentCard(resolveAgentCard());
    }

    static String extractTenantFromAgentCard(AgentCard agentCard) {
        try {
            if (agentCard == null || agentCard.supportedInterfaces() == null) {
                return null;
            }
            return agentCard.supportedInterfaces().stream()
                    .map(AgentInterface::tenant)
                    .filter(t -> t != null && !t.isEmpty())
                    .findFirst()
                    .orElse(null);
        } catch (RuntimeException ex) {
            LOG.debug("Couldn't extract tenant from agent card", ex);
            return null;
        }
    }
}
