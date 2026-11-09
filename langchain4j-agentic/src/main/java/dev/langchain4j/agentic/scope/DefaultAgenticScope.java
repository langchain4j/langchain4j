package dev.langchain4j.agentic.scope;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static dev.langchain4j.agentic.internal.AgentUtil.keyDefaultValue;
import static dev.langchain4j.agentic.internal.AgentUtil.keyName;
import static dev.langchain4j.agentic.observability.ListenerNotifierUtil.onAgenticSystemSuspended;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.Internal;
import dev.langchain4j.agentic.agent.AgentInvocationException;
import dev.langchain4j.agentic.agent.ErrorContext;
import dev.langchain4j.agentic.agent.ErrorRecoveryResult;
import dev.langchain4j.agentic.declarative.TypedKey;
import dev.langchain4j.agentic.internal.AsyncResponse;
import dev.langchain4j.agentic.internal.DeferredResponse;
import dev.langchain4j.agentic.internal.DelayedResponse;
import dev.langchain4j.agentic.internal.SuspendedResponse;
import dev.langchain4j.agentic.internal.UserMessageTransformer;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.decision.DecisionResult;
import dev.langchain4j.service.memory.ChatMemoryAccess;
import dev.langchain4j.service.tool.ToolExecution;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Internal
@JsonInclude(NON_NULL)
public class DefaultAgenticScope implements AgenticScope {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultAgenticScope.class);

    @JsonInclude(NON_NULL)
    public record AgentMessage(
            @JsonProperty("agentName") String agentName,
            @JsonProperty("agentId") String agentId,
            @JsonProperty("message") ChatMessage message) {}

    private final Object memoryId;
    private final Map<String, Object> state = new ConcurrentHashMap<>();
    // Iterators are snapshots, so a reader can walk either list while another thread registers an
    // invocation or appends messages, without ConcurrentModificationException.
    private final List<AgentInvocation> agentInvocations = new CopyOnWriteArrayList<>();
    private final List<AgentMessage> context = new CopyOnWriteArrayList<>();

    private final transient Map<String, Object> agents = new ConcurrentHashMap<>();
    private final transient Map<String, Object> executionContexts = new ConcurrentHashMap<>();
    private final transient List<CompensableExecution> compensableExecutions =
            Collections.synchronizedList(new ArrayList<>());

    private static final Function<ErrorContext, ErrorRecoveryResult> DEFAULT_ERROR_RECOVERY =
            errorContext -> ErrorRecoveryResult.throwException();

    private transient Function<ErrorContext, ErrorRecoveryResult> errorHandler = DEFAULT_ERROR_RECOVERY;

    /**
     * The registry where a persistent scope is saved again when an asynchronous agent, which the root call does not
     * wait for, completes after the end of that call: set at the end of the root call and cleared at the start of
     * the next one, so that it also tells whether the root call ended, see {@link #asyncEnded(String, AsyncResponse)}.
     */
    private transient volatile AgenticScopeRegistry registryAfterRootCall;

    /**
     * The asynchronous agents of this scope still running, by name: the next root call on this scope waits for them,
     * see {@link #rootCallStarted(AgenticScopeRegistry)}.
     */
    private final transient Map<CompletableFuture<Void>, String> runningAsyncAgents = new ConcurrentHashMap<>();

    private transient boolean evicted;

    private static Predicate<Object> serializableStateFilter = Predicate.not(DefaultAgenticScope::isProxy)
            .and(Predicate.not(DefaultAgenticScope::isTokenStream))
            .and(Predicate.not(DefaultAgenticScope::isFuture))
            // the raw response of a decision model, written by decision agents returning a DecisionResult, and the
            // records answering several questions with Choice, Scale or YesNoAnswer fields, which cannot be converted
            .and(Predicate.not(
                    value -> value instanceof DecisionResult<?> || PersistedDecisionAnswers.isCompositeAnswer(value)));

    private static boolean isProxy(Object obj) {
        return Proxy.isProxyClass(obj.getClass());
    }

    private static boolean isTokenStream(Object obj) {
        return obj instanceof TokenStream;
    }

    private static boolean isFuture(Object obj) {
        return obj instanceof Future;
    }

    public enum Kind {
        EPHEMERAL,
        REGISTERED,
        PERSISTENT
    }

    private final Kind kind;

    /**
     * Replaces every registered invocation with the one returned by the given converter, for a JSON codec adapting
     * the values of the invocations before writing the scope or after reading it.
     */
    void replaceAgentInvocations(UnaryOperator<AgentInvocation> converter) {
        agentInvocations.replaceAll(converter);
    }

    /**
     * The state that is safe to persist. Public so that a JSON codec supplied through the SPI can
     * reach it from another package.
     */
    public DefaultAgenticScope serializableCopy() {
        DefaultAgenticScope copy = new DefaultAgenticScope(memoryId, kind);
        state.forEach((key, value) -> {
            // the response of an asynchronous agent is left out while it is still running or when it failed, since
            // the scope can be saved without waiting for it: its result takes its place when it completes
            if (isSerializable(value) && !(value instanceof AsyncResponse)) {
                copy.state.put(key, value);
            }
        });
        agentInvocations.forEach(invocation -> copy.agentInvocations.add(invocation.persistable()));
        copy.context.addAll(context);
        return copy;
    }

    public static boolean isSerializable(Object value) {
        return value == null || serializableStateFilter.test(value);
    }

    public static void addSerializableStateFilter(Predicate<Object> filter) {
        serializableStateFilter = serializableStateFilter.and(filter);
    }

    /**
     * This lock is used to ensure that the AgenticScope doesn't get concurrently modified when it is going to be persisted.
     * The internal data structures of the AgenticScope are all thread-safe, so they don't need to be guarded by a read lock
     * when accessed. In essence multiple changes are allowed at the same time, but it is not allowed to persist a
     * AgenticScope that is not in a frozen state. That's why the read lock is acquired for the first and a write lock
     * when the second happens.
     */
    private final transient ReadWriteLock lock;

    DefaultAgenticScope(Kind kind) {
        this(Utils.randomUUID(), kind);
    }

    @JsonCreator
    DefaultAgenticScope(@JsonProperty("memoryId") Object memoryId, @JsonProperty("kind") Kind kind) {
        this.memoryId = memoryId;
        this.kind = kind;
        this.lock = (kind == Kind.PERSISTENT) ? new ReentrantReadWriteLock() : null;
    }

    public static DefaultAgenticScope ephemeralAgenticScope() {
        return new DefaultAgenticScope(DefaultAgenticScope.Kind.EPHEMERAL);
    }

    @Override
    public Object memoryId() {
        return memoryId;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void writeState(String key, Object value) {
        withReadLock(() -> {
            Object old;
            if (value == null) {
                old = state.remove(key);
            } else {
                old = state.put(key, value);
            }
            if (old instanceof DeferredResponse<?> pending && !pending.isDone()) {
                ((DeferredResponse<Object>) pending).complete(value);
            }
        });
    }

    @Override
    public <T> void writeState(Class<? extends TypedKey<T>> key, T value) {
        writeState(keyName(key), value);
    }

    @Override
    public void writeStateIfAbsent(String key, Object value) {
        if (value != null) {
            withReadLock(() -> state.compute(key, (k, v) -> hasState(k) ? v : value));
        }
    }

    @Override
    public <T> void writeStateIfAbsent(Class<? extends TypedKey<T>> key, T value) {
        writeStateIfAbsent(keyName(key), value);
    }

    @Override
    public void writeStates(Map<String, Object> newState) {
        withReadLock(() -> state.putAll(newState));
    }

    @Override
    public boolean hasState(String key) {
        Object value = state.get(key);
        if (value == null) {
            return false;
        }
        return value instanceof String s ? !s.isBlank() : true;
    }

    @Override
    public boolean hasState(Class<? extends TypedKey<?>> key) {
        return hasState(keyName(key));
    }

    @Override
    public Object readState(String key) {
        return readStateBlocking(key, state.get(key));
    }

    @Override
    public <T> T readState(String key, T defaultValue) {
        return (T) readStateBlocking(key, state.getOrDefault(key, defaultValue));
    }

    @Override
    public <T> T readState(Class<? extends TypedKey<T>> key) {
        return readState(keyName(key), keyDefaultValue(key));
    }

    private Object readStateBlocking(String key, Object state) {
        if (state instanceof DelayedResponse asyncResponse) {
            state = asyncResponse.blockingGet();
            writeState(key, state);
        }
        return state;
    }

    @Override
    public Map<String, Object> state() {
        return state;
    }

    public <T> T getOrCreateAgent(String agentId, Function<DefaultAgenticScope, T> agentFactory) {
        return (T) agents.computeIfAbsent(agentId, id -> agentFactory.apply(this));
    }

    /**
     * The agents created for this scope only, by {@link #getOrCreateAgent}: the AI agents whose configuration
     * depends on the scope, and the planner agent of a supervisor. They are not part of the agent tree of the
     * agentic system, so they have to be reached through the scope, for instance to evict their chat memories.
     */
    public Collection<Object> cachedAgents() {
        return Collections.unmodifiableCollection(agents.values());
    }

    public void registerAgentInvocation(AgentInvocation agentInvocation) {
        withReadLock(() -> agentInvocations.add(agentInvocation));
    }

    /**
     * Adds to the context of this scope the messages exchanged by an AI agent in the invocation that just
     * completed: the last user message and the last answer of its chat memory when it has one, otherwise the user
     * message of its last model call, when known, and its output. Other kinds of agents have no messages to add.
     */
    public void registerContext(AgentInstance agent, Object agentObject, UserMessage userMessage, Object output) {
        if (!(agentObject instanceof ChatMemoryAccess agentWithMemory)) {
            return;
        }
        withReadLock(() -> {
            ChatMemory chatMemory = agentWithMemory.getChatMemory(memoryId);
            if (chatMemory != null) {
                registerContextFromChatMemory(agent.name(), agent.agentId(), chatMemory);
            } else if (output != null) {
                Object outputText = isSerializable(output) ? output : "<unknown>";
                addToContext(agent.name(), agent.agentId(), userMessage, AiMessage.aiMessage(outputText.toString()));
            }
        });
    }

    private void addToContext(String agentName, String agentId, UserMessage userMessage, AiMessage aiMessage) {
        AgentMessage agentAiMessage = new AgentMessage(agentName, agentId, aiMessage);
        // added together, so that the messages of concurrent invocations do not interleave in the context
        context.addAll(
                userMessage == null
                        ? List.of(agentAiMessage)
                        : List.of(new AgentMessage(agentName, agentId, userMessage), agentAiMessage));
    }

    /**
     * Starts a root call, once the asynchronous agents of the previous call on this scope still running completed:
     * one root call at a time runs on a scope, since those agents would otherwise share with the new call the chat
     * memory of their agents, the context and the compensations of the scope and the execution recorded by an
     * {@code AgentMonitor}. The tool executions of the agents completed after the end of the previous call are then
     * dropped, as those of a completed root call are: a failure of this call does not compensate them. The failed
     * responses of those agents are dropped too, as the saved copy of a persistent scope never contains them: their
     * failure was reported, and an agent of this call must not fail for it, like a skipped agent returning the
     * current value of its output key would.
     */
    public void rootCallStarted(AgenticScopeRegistry registry) {
        if (!runningAsyncAgents.isEmpty()) {
            Map<CompletableFuture<Void>, String> running = Map.copyOf(runningAsyncAgents);
            LOG.debug(
                    "Root call on AgenticScope '{}' waits for the asynchronous agents {} of the previous call, still running",
                    memoryId,
                    running.values());
            running.keySet().forEach(CompletableFuture::join);
        }
        compensableExecutions.clear();
        withReadLock(() ->
                state.values().removeIf(value -> value instanceof AsyncResponse<?> response && response.isFailed()));
        registryAfterRootCall = null;
    }

    /**
     * Suspends the root call, which returns to its caller to be resumed by a later call on this scope. As after a
     * completed call, the failure of an asynchronous agent completing afterwards is reported, and a persistent scope
     * is saved again with its result. The suspension of an agent already completed is not reported: it is among
     * the pending responses the root call just notified.
     */
    public void rootCallSuspended(AgenticScopeRegistry registry, AgentListener agentListener) {
        Map.copyOf(state).forEach((key, value) -> {
            if (value instanceof AsyncResponse<?> unread) {
                if (unread.isDone()) {
                    reportUnreadFailure(key, unread, null, false);
                } else {
                    unread.onComplete(completed -> reportUnreadFailure(key, completed, agentListener, false));
                }
            }
        });
        if (kind == Kind.PERSISTENT) {
            registryAfterRootCall = registry;
        }
    }

    /**
     * Ends the root call, which {@code failed} tells whether it ended with an exception. The responses of the
     * asynchronous agents that no agent of the root call read are left in place, and the root call does not wait
     * for them: their failure is logged on completion when the call succeeded, since no agent would observe it
     * otherwise, while a failed root call already surfaced the failure of the response it read, and a suspension of
     * the agentic system by one of them, which the call returned without noticing, is notified to the listener.
     */
    public void rootCallEnded(AgenticScopeRegistry registry, AgentListener agentListener, boolean failed) {
        // ensure that all pending streaming and deferred responses are completed before ending the root call
        List<String> stillRunning = new ArrayList<>();
        Map.copyOf(state).forEach((key, value) -> {
            if (value instanceof AsyncResponse<?> unread) {
                if (!unread.isDone()) {
                    stillRunning.add(key);
                }
                unread.onComplete(completed -> reportUnreadFailure(key, completed, agentListener, failed));
            } else if (value instanceof DelayedResponse<?> pending && !suspendsTheSystem(pending)) {
                writeState(key, pending.blockingGet());
            }
        });
        if (!stillRunning.isEmpty()) {
            LOG.debug(
                    "Root call on AgenticScope '{}' ended without waiting for the asynchronous agents producing {}, still running",
                    memoryId,
                    stillRunning);
        }

        if (kind == Kind.EPHEMERAL) {
            // Ephemeral agenticScope are for single-use and can be evicted immediately
            registry.evict(memoryId, agentListener);
        } else if (kind == Kind.PERSISTENT) {
            // saved now without the results still pending, and again when each of them arrives: the registry is
            // published before saving, so that a result arriving during the save is saved again by its agent
            registryAfterRootCall = registry;
            flush(registry);
        }

        compensableExecutions.clear();
    }

    /**
     * A suspended response not yet completed is meant to suspend the agentic system, never to be awaited: it is left
     * in place for the next root call on this scope to resume from it.
     */
    private static boolean suspendsTheSystem(DelayedResponse<?> response) {
        return response instanceof SuspendedResponse<?> suspended && !suspended.isDone();
    }

    /**
     * Reports once the failure of the response of an asynchronous agent that no agent of the root call read: a
     * suspension of the agentic system is notified to the listener, as the root call returned without noticing it
     * and its caller cannot get it anymore, while any other failure is logged, unless the root call failed, since
     * the failure it surfaced may be the one of the response it read.
     */
    private void reportUnreadFailure(
            String outputKey, AsyncResponse<?> response, AgentListener agentListener, boolean rootCallFailed) {
        if (!response.reportFailureOnce()) {
            return;
        }
        try {
            response.blockingGet();
        } catch (AgenticSystemSuspendedException e) {
            onAgenticSystemSuspended(agentListener, this);
        } catch (RuntimeException e) {
            if (!rootCallFailed) {
                LOG.warn(
                        "Asynchronous agent producing '{}' failed and the agentic system did not read its result",
                        outputKey,
                        e);
            }
        }
    }

    /**
     * Tracks the response of an asynchronous agent just written to this scope: its result takes the place of the
     * response when it completes, see {@link #asyncEnded(String, AsyncResponse)}, and until then the next root call
     * on this scope waits for it. Called after the write, so that the result of an agent completing before it is
     * settled anyway.
     */
    public void asyncPending(String agentName, String outputKey, AsyncResponse<?> response) {
        CompletableFuture<Void> ended = new CompletableFuture<>();
        runningAsyncAgents.put(ended, agentName);
        response.onComplete(completed -> {
            try {
                asyncEnded(outputKey, completed);
            } finally {
                runningAsyncAgents.remove(ended);
                ended.complete(null);
            }
        });
    }

    /**
     * Completes an asynchronous agent, on the thread of its executor. Its result replaces the response under its
     * output key, if the key still holds that response, as reading it would have done, while a failure is left in
     * place, so that reading it still fails, until the next root call on this scope drops it. A persistent scope
     * whose root call already ended is then saved again, with the result.
     */
    public void asyncEnded(String outputKey, AsyncResponse<?> response) {
        if (outputKey != null && !outputKey.isBlank() && !response.isFailed()) {
            Object result = response.blockingGet();
            withReadLock(() -> {
                if (result == null) {
                    state.remove(outputKey, response);
                } else {
                    state.replace(outputKey, response, result);
                }
            });
        }
        AgenticScopeRegistry registry = registryAfterRootCall;
        if (registry != null) {
            try {
                flush(registry);
            } catch (RuntimeException e) {
                LOG.error(
                        "Failed to save AgenticScope '{}' after the completion of its asynchronous agent", memoryId, e);
            }
        }
    }

    /**
     * Called by the registry when it evicts this scope, before deleting it from the store, so that an asynchronous
     * agent completing afterwards does not save it again.
     */
    void markEvicted() {
        if (kind != Kind.PERSISTENT) {
            return;
        }
        lock.writeLock().lock();
        try {
            evicted = true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void flush(AgenticScopeRegistry registry) {
        lock.writeLock().lock();
        try {
            if (!evicted) {
                registry.update(this);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void registerContextFromChatMemory(String agentName, String agentId, ChatMemory chatMemory) {
        List<ChatMessage> agentMessages = chatMemory.messages();
        if (Utils.isNullOrEmpty(agentMessages)) {
            return;
        }

        ChatMessage lastMessage = agentMessages.get(agentMessages.size() - 1);
        if (!(lastMessage instanceof AiMessage aiMessage)) {
            return;
        }

        for (int i = agentMessages.size() - 1; i >= 0; i--) {
            if (agentMessages.get(i) instanceof UserMessage userMessage) {
                // Only add to the agenticScope's context the last UserMessage and the last AiMessage response, all
                // other messages are local to the invoked agent internals
                addToContext(agentName, agentId, userMessage, aiMessage);
                return;
            }
        }
    }

    public List<AgentMessage> context() {
        return context;
    }

    @Override
    public String contextAsConversation(Object... agents) {
        Predicate<String> agentFilter = agents != null && agents.length > 0
                ? Arrays.stream(agents)
                        .filter(AgentInstance.class::isInstance)
                        .map(AgentInstance.class::cast)
                        .map(AgentInstance::name)
                        .toList()::contains
                : agent -> true;
        return contextAsConversation(agentFilter);
    }

    @Override
    public String contextAsConversation(String... agentNames) {
        Predicate<String> agentFilter =
                agentNames != null && agentNames.length > 0 ? List.of(agentNames)::contains : agent -> true;
        return contextAsConversation(agentFilter);
    }

    private String contextAsConversation(Predicate<String> agentFilter) {
        StringBuilder sb = new StringBuilder();
        for (AgentMessage agentMessage : context) {
            if (!agentFilter.test(agentMessage.agentName())) {
                continue;
            }
            ChatMessage message = agentMessage.message();
            if (message instanceof UserMessage userMessage) {
                sb.append("User: \"").append(text(userMessage)).append("\"\n");
            } else if (message instanceof AiMessage aiMessage) {
                sb.append(agentMessage.agentName())
                        .append(" agent: \"")
                        .append(aiMessage.text())
                        .append("\"\n");
            }
        }

        String contextAsConversation = sb.toString();
        LOG.trace("AgenticScope context as conversation: '{}'", contextAsConversation);
        return contextAsConversation;
    }

    /**
     * The same text of the given user message that a {@link UserMessageTransformer} transforms, or, for a message
     * without any text, like a message made only of an image, the types of its contents, like {@code [image]}, so
     * that the conversation still tells that the user sent something.
     */
    private static String text(UserMessage userMessage) {
        String text = UserMessageTransformer.text(userMessage);
        if (!text.isEmpty() || userMessage.contents().isEmpty()) {
            return text;
        }
        return userMessage.contents().stream()
                .map(content -> "[" + content.type().name().toLowerCase(Locale.ROOT) + "]")
                .collect(Collectors.joining(" "));
    }

    @Override
    public List<AgentInvocation> agentInvocations() {
        return Collections.unmodifiableList(agentInvocations);
    }

    @Override
    public List<AgentInvocation> agentInvocations(String agentName) {
        return agentInvocations.stream()
                .filter(inv -> inv.agentName().equals(agentName))
                .toList();
    }

    @Override
    public List<AgentInvocation> agentInvocations(Class<?> agentType) {
        return agentInvocations.stream()
                .filter(inv -> inv.agentType().equals(agentType))
                .toList();
    }

    @Override
    public String toString() {
        StringBuilder sb =
                new StringBuilder("AgenticScope{memoryId='").append(memoryId).append("', state={");
        boolean first = true;
        for (Map.Entry<String, Object> e : state.entrySet()) {
            if (!first) sb.append(", ");
            first = false;
            sb.append(e.getKey()).append('=');
            // Guard against circular references (e.g. ResultWithAgenticScope holding this scope)
            if (e.getValue() instanceof ResultWithAgenticScope<?>) {
                sb.append("<ResultWithAgenticScope>");
            } else {
                sb.append(e.getValue());
            }
        }
        sb.append("}}");
        return sb.toString();
    }

    private void withReadLock(Runnable action) {
        if (kind == Kind.PERSISTENT) {
            lock.readLock().lock();
            try {
                action.run();
            } finally {
                lock.readLock().unlock();
            }
        } else {
            action.run();
        }
    }

    public DefaultAgenticScope withErrorHandler(Function<ErrorContext, ErrorRecoveryResult> errorHandler) {
        if (errorHandler != null) {
            this.errorHandler = errorHandler;
        }
        return this;
    }

    public ErrorRecoveryResult handleError(String agentName, AgentInvocationException exception) {
        return errorHandler.apply(new ErrorContext(agentName, this, exception));
    }

    private record CompensableExecution(ToolExecution toolExecution, Consumer<ToolExecution> compensatingAction) {

        public void compensate() {
            try {
                compensatingAction.accept(toolExecution());
            } catch (Exception e) {
                LOG.warn(
                        "Cross-agent compensating action failed for tool '{}': {}",
                        toolExecution().request().name(),
                        e.getMessage(),
                        e);
            }
        }
    }

    public void registerCompensableExecution(ToolExecution toolExecution, Consumer<ToolExecution> compensatingAction) {
        compensableExecutions.add(new CompensableExecution(toolExecution, compensatingAction));
    }

    public void compensateAll() {
        List<CompensableExecution> snapshot;
        synchronized (compensableExecutions) {
            snapshot = new ArrayList<>(compensableExecutions);
            compensableExecutions.clear();
        }

        for (int i = snapshot.size() - 1; i >= 0; i--) {
            snapshot.get(i).compensate();
        }
    }

    /**
     * Checkpoints the current state of this scope by persisting it to the store.
     * This is a no-op for non-persistent scopes. For persistent scopes, it acquires
     * the write lock and flushes the current state to the store.
     *
     * @param registry the registry managing this scope's persistence
     */
    public void checkpoint(AgenticScopeRegistry registry) {
        if (kind == Kind.PERSISTENT) {
            flush(registry);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean completePendingResponse(String responseId, Object value) {
        for (Map.Entry<String, Object> entry : state.entrySet()) {
            if (entry.getValue() instanceof DeferredResponse<?> deferred
                    && deferred.responseId().equals(responseId)) {
                boolean completed = ((DeferredResponse<Object>) deferred).complete(value);
                if (completed) {
                    writeState(entry.getKey(), value);
                }
                return completed;
            }
        }
        return false;
    }

    @Override
    public Set<String> pendingResponseIds() {
        return state.values().stream()
                .filter(DeferredResponse.class::isInstance)
                .map(DeferredResponse.class::cast)
                .filter(p -> !p.isDone())
                .map(DeferredResponse::responseId)
                .collect(Collectors.toSet());
    }

    @Override
    public void writeExecutionContext(final String key, final Object context) {
        if (key == null) throw new IllegalArgumentException("key cannot be null");
        if (context == null) throw new IllegalArgumentException("context cannot be null");
        this.executionContexts.put(key, context);
    }

    @Override
    public Object executionContext(final String key) {
        return this.executionContexts.get(key);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T executionContextAs(final String key, final Class<T> type) {
        return (T) this.executionContexts.get(key);
    }
}
