package dev.langchain4j.agentic.scope;

import static dev.langchain4j.agentic.internal.AgentUtil.keyDefaultValue;
import static dev.langchain4j.agentic.internal.AgentUtil.keyName;

import dev.langchain4j.Internal;
import dev.langchain4j.agentic.agent.AgentInvocationException;
import dev.langchain4j.agentic.agent.ChatMessagesAccess;
import dev.langchain4j.agentic.agent.ErrorContext;
import dev.langchain4j.agentic.agent.ErrorRecoveryResult;
import dev.langchain4j.agentic.declarative.TypedKey;
import dev.langchain4j.agentic.internal.DelayedResponse;
import dev.langchain4j.agentic.internal.DeferredResponse;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.memory.ChatMemoryAccess;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import dev.langchain4j.service.tool.ToolExecution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Internal
public class DefaultAgenticScope implements AgenticScope {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultAgenticScope.class);

    public record AgentMessage(String agentName, String agentId, ChatMessage message) {}

    private final Object memoryId;
    private final Map<String, Object> state = new ConcurrentHashMap<>();
    private final List<AgentInvocation> agentInvocations = Collections.synchronizedList(new ArrayList<>());
    private final List<AgentMessage> context = Collections.synchronizedList(new ArrayList<>());

    private final transient Map<String, Object> agents = new ConcurrentHashMap<>();
    private final transient Map<String, Object> executionContexts = new ConcurrentHashMap<>();
    private final transient List<CompensableExecution> compensableExecutions = Collections.synchronizedList(new ArrayList<>());
    private volatile boolean compensateOnError = false;

    private static final Function<ErrorContext, ErrorRecoveryResult> DEFAULT_ERROR_RECOVERY =
            errorContext -> ErrorRecoveryResult.throwException();

    private transient Function<ErrorContext, ErrorRecoveryResult> errorHandler = DEFAULT_ERROR_RECOVERY;

    private static Predicate<Object> serializableStateFilter = Predicate.not(DefaultAgenticScope::isProxy)
            .and(Predicate.not(DefaultAgenticScope::isTokenStream)).and(Predicate.not(DefaultAgenticScope::isFuture));

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

    DefaultAgenticScope serializableCopy() {
        DefaultAgenticScope copy = new DefaultAgenticScope(memoryId, kind);
        state.forEach((key, value) -> {
            if (isSerializable(value)) {
                copy.state.put(key, value);
            }
        });
        copy.agentInvocations.addAll(agentInvocations);
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

    DefaultAgenticScope(Object memoryId, Kind kind) {
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

    public void registerAgentInvocation(AgentInvocation agentInvocation, Object agent) {
        withReadLock(() -> {
            agentInvocations.add(agentInvocation);
            registerContext(agentInvocation, agent);
        });
    }

    public void rootCallStarted(AgenticScopeRegistry registry) {}

    public void rootCallEnded(AgenticScopeRegistry registry, AgentListener agentListener) {
        // ensure that all pending async operations are completed before ending the root call
        state.replaceAll(this::readStateBlocking);

        if (kind == Kind.EPHEMERAL) {
            // Ephemeral agenticScope are for single-use and can be evicted immediately
            registry.evict(memoryId, agentListener);
        } else if (kind == Kind.PERSISTENT) {
            flush(registry);
        }

        compensableExecutions.clear();
    }

    private void flush(AgenticScopeRegistry registry) {
        lock.writeLock().lock();
        try {
            registry.update(this);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void registerContext(AgentInvocation agentInvocation, Object agent) {
        ChatMemory chatMemory =
                agent instanceof ChatMemoryAccess agentWithMemory ? agentWithMemory.getChatMemory(memoryId) : null;
        if (chatMemory != null) {
            registerContextFromChatMemory(agentInvocation, chatMemory);
        } else if (agentInvocation.output() != null && agent instanceof ChatMessagesAccess chatMessagesAccess) {
            context.add(new AgentMessage(
                    agentInvocation.agentName(),
                    agentInvocation.agentId(),
                    chatMessagesAccess.lastUserMessage(memoryId())));
            context.add(new AgentMessage(
                    agentInvocation.agentName(),
                    agentInvocation.agentId(),
                    AiMessage.aiMessage(agentInvocation.output().toString())));
            chatMessagesAccess.removeLastResponseEvent(memoryId());
        }
    }

    private void registerContextFromChatMemory(AgentInvocation agentInvocation, ChatMemory chatMemory) {
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
                // Only add to the agenticScope's context the last UserMessage ...
                context.add(new AgentMessage(agentInvocation.agentName(), agentInvocation.agentId(), userMessage));
                // ... and last AiMessage response, all other messages are local to the invoked agent internals
                context.add(new AgentMessage(agentInvocation.agentName(), agentInvocation.agentId(), aiMessage));
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
                sb.append("User: \"").append(userMessage.singleText()).append("\"\n");
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

    @Override
    public List<AgentInvocation> agentInvocations() {
        return agentInvocations;
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
        return "AgenticScope{" + "memoryId='" + memoryId + '\'' + ", state=" + state + '}';
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
                LOG.warn("Cross-agent compensating action failed for tool '{}': {}",
                        toolExecution().request().name(), e.getMessage(), e);
            }
        }
    }

    public void compensateOnError(boolean compensateOnError) {
        this.compensateOnError = compensateOnError;
    }

    public void registerCompensableExecution(ToolExecution toolExecution, Consumer<ToolExecution> compensatingAction) {
        if (compensateOnError) {
            compensableExecutions.add(new CompensableExecution(toolExecution, compensatingAction));
        }
    }

    public void compensateAll() {
        if (!compensateOnError) {
            return;
        }

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
                    withReadLock(() -> state.put(entry.getKey(), value));
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
