package dev.langchain4j.agentic.scope;

import dev.langchain4j.Internal;
import dev.langchain4j.agentic.agent.AgentInvocationException;
import dev.langchain4j.agentic.agent.ErrorContext;
import dev.langchain4j.agentic.agent.ErrorRecoveryResult;
import dev.langchain4j.agentic.internal.AgentInvocation;
import dev.langchain4j.agentic.internal.AsyncResponse;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.service.memory.ChatMemoryAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.function.Predicate;

@Internal
public class DefaultAgenticScope implements AgenticScope {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultAgenticScope.class);

    public record AgentMessage(String agentName, ChatMessage message) {}

    private final Object memoryId;
    private final Map<String, Object> state = new ConcurrentHashMap<>();
    private final Map<String, List<AgentInvocation>> agentInvocations = new ConcurrentHashMap<>();
    private final List<AgentMessage> context = Collections.synchronizedList(new ArrayList<>());

    private final transient Map<String, Object> agents = new ConcurrentHashMap<>();

    private static final Function<ErrorContext, ErrorRecoveryResult> DEFAULT_ERROR_RECOVERY =
            errorContext -> ErrorRecoveryResult.throwException();

    private transient Function<ErrorContext, ErrorRecoveryResult> errorHandler = DEFAULT_ERROR_RECOVERY;

    public enum Kind {
        EPHEMERAL, REGISTERED, PERSISTENT
    }
    private final Kind kind;

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

    @Override
    public Object memoryId() {
        return memoryId;
    }

    @Override
    public void writeState(String key, Object value) {
        withReadLock(() -> {
            if (value == null) {
                state.remove(key);
            } else {
                state.put(key, value);
            }
        });
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
    public Object readState(String key) {
        return readStateBlocking(key, state.get(key));
    }

    @Override
    public <T> T readState(String key, T defaultValue) {
        return (T) readStateBlocking(key, state.getOrDefault(key, defaultValue));
    }

    private Object readStateBlocking(String key, Object state) {
        if (state instanceof AsyncResponse asyncResponse) {
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

    public void registerAgentCall(String agentName, Object agent, Object[] input, Object output) {
        withReadLock(() -> {
            agentInvocations.computeIfAbsent(agentName, name -> new ArrayList<>())
                            .add(new AgentInvocation(agentName, input, output));
            registerContext(agentName, agent);
        });
    }

    public void rootCallStarted(AgenticScopeRegistry registry) {
    }

    public void rootCallEnded(AgenticScopeRegistry registry) {
        if (kind == Kind.EPHEMERAL) {
            // Ephemeral agenticScope are for single-use and can be evicted immediately
            registry.evict(memoryId);
        } else if (kind == Kind.PERSISTENT) {
            flush(registry);
        }
    }

    private void flush(AgenticScopeRegistry registry) {
        lock.writeLock().lock();
        try {
            registry.update(this);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void registerContext(String agentName, Object agent) {
    	if (!(agent instanceof ChatMemoryAccess agentWithMemory)) {
    		return;
    	}
        
    	ChatMemory chatMemory = agentWithMemory.getChatMemory(memoryId);
    	if (chatMemory == null) {
    		return;
    	}
        
    	List<ChatMessage> agentMessages = chatMemory.messages();
    	if(Utils.isNullOrEmpty(agentMessages)) {
    		return;
    	}
        
    	ChatMessage lastMessage = agentMessages.get(agentMessages.size() - 1);
    	if (!(lastMessage instanceof AiMessage aiMessage)) {
    		return;
    	}
        
        for (int i = agentMessages.size() - 1; i >= 0; i--) {
        	if (agentMessages.get(i) instanceof UserMessage userMessage) {
        		// Only add to the agenticScope's context the last UserMessage ...
        		context.add(new AgentMessage(agentName, userMessage));
        		// ... and last AiMessage response, all other messages are local to the invoked agent internals
        		context.add(new AgentMessage(agentName, aiMessage));
        		return;
        	}
        }       
    }

    public List<AgentMessage> context() {
        return context;
    }

    @Override
    public String contextAsConversation(String... agentNames) {
        Predicate<String> agentFilter = agentNames != null && agentNames.length > 0 ?
                List.of(agentNames)::contains :
                agent -> true;

        StringBuilder sb = new StringBuilder();
        for (AgentMessage agentMessage : context) {
            if (!agentFilter.test(agentMessage.agentName())) {
                continue;
            }
            ChatMessage message = agentMessage.message();
            if (message instanceof UserMessage userMessage) {
                sb.append("User: ").append(userMessage.singleText()).append("\n");
            } else if (message instanceof AiMessage aiMessage) {
                sb.append(agentMessage.agentName()).append(" agent: ").append(aiMessage.text()).append("\n");
            }
        }

        String contextAsConversation = sb.toString();
        LOG.debug("AgenticScope context as conversation: '{}'", contextAsConversation);
        return contextAsConversation;
    }

    public List<AgentInvocation> agentInvocations(String agentName) {
        return agentInvocations.getOrDefault(agentName, List.of());
    }

    @Override
    public String toString() {
        return "AgenticScope{" +
                "memoryId='" + memoryId + '\'' +
                ", state=" + state +
                '}';
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
}
