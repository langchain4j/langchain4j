package dev.langchain4j.agentic.cognisphere;

import dev.langchain4j.agentic.internal.AgentCall;
import dev.langchain4j.agentic.internal.AgentSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Predicate;

/**
 * The Cognisphere class represents a cognitive environment where agents belonging to the same
 * agentic system can share their state.
 * It maintains the state of the environment, tracks agent invocations, and provides
 * methods to allow agents to interact with the shared state.
 * <p>
 * Agents can register their calls, and the context of interactions is stored for later retrieval.
 * The class also provides methods to read and write state, manage agent invocations, and retrieve
 * the context as a conversation.
 */
public class Cognisphere {

    private static final Logger LOG = LoggerFactory.getLogger(Cognisphere.class);

    public record AgentMessage(String agentName, ChatMessage message) {}

    private final CognisphereKey key;
    private final Map<String, Object> state = new ConcurrentHashMap<>();
    private final Map<String, List<AgentCall>> agentsInvocations = new ConcurrentHashMap<>();
    private final List<AgentMessage> context = Collections.synchronizedList(new ArrayList<>());

    public enum Kind {
        EPHEMERAL, REGISTERED, PERSISTENT
    }
    private final Kind kind;

    /**
     * This lock is used to ensure that the Cognisphere doesn't get concurrently modified when it is going to be persisted.
     * The internal data structures of the Cognisphere are all thread-safe, so they don't need to be guarded by a read lock
     * when accessed. In essence multiple changes are allowed at the same time, but it is not allowed to persist a
     * Cognisphere that is not in a frozen state. That's why the read lock is acquired for the firsts and a write lock
     * when the second happens.
     */
    private transient ReadWriteLock lock = null;

    Cognisphere(Kind kind) {
        this(new CognisphereKey("", UUID.randomUUID().toString()), kind);
    }

    Cognisphere(CognisphereKey key, Kind kind) {
        this.key = key;
        this.kind = kind;
        if (kind == Kind.PERSISTENT) {
            lock = new ReentrantReadWriteLock();
        }
    }

    public CognisphereKey key() {
        return key;
    }

    public Object id() {
        return key.id();
    }

    public void writeState(String key, Object value) {
        withReadLock(() -> {
            if (value == null) {
                state.remove(key);
            } else {
                state.put(key, value);
            }
        });
    }

    public void writeStates(Map<String, Object> newState) {
        withReadLock(() -> state.putAll(newState));
    }

    public boolean hasState(String key) {
        Object value = state.get(key);
        if (value == null) {
            return false;
        }
        return value instanceof String s ? !s.isBlank() : true;
    }

    public Object readState(String key) {
        return state.get(key);
    }

    public <T> T readState(String key, T defaultValue) {
        return (T) state.getOrDefault(key, defaultValue);
    }

    public Map<String, Object> getState() {
        return state;
    }

    public void registerAgentCall(AgentSpecification agentSpecification, Object agent, Object[] input, Object response) {
        withReadLock(() -> {
            agentsInvocations.computeIfAbsent(agentSpecification.name(), name -> new ArrayList<>())
                            .add(new AgentCall(agentSpecification.name(), input, response));
            registerContext(agentSpecification, agent, input, response);
        });
    }

    public void rootCallStarted() {
        CognisphereRegistry.setCurrentAgentId(key.agentId());
    }

    public void rootCallEnded() {
        if (kind == Kind.EPHEMERAL) {
            // Ephemeral cognispheres are for single-use and can be evicted immediately
            CognisphereRegistry.evict(key);
        } else if (kind == Kind.PERSISTENT) {
            flush();
        }
        CognisphereRegistry.resetCurrentAgentId();
    }

    private void flush() {
        lock.writeLock().lock();
        try {
            CognisphereRegistry.update(this);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void registerContext(AgentSpecification agentSpecification, Object agent, Object[] input, Object response) {
        if (agent instanceof ChatMemoryAccess agentWithMemory) {
            ChatMemory chatMemory = agentWithMemory.getChatMemory(key.id());
            if (chatMemory != null) {
                List<ChatMessage> agentMessages = chatMemory.messages();
                for (int i = agentMessages.size() - 1; i >= 0; i--) {
                    if (agentMessages.get(i) instanceof UserMessage userMessage) {
                        // Only add to the cognisphere's context the last UserMessage ...
                        context.add(new AgentMessage(agentSpecification.name(), userMessage));
                        // ... and last AiMessage response, all other messages are local to the invoked agent internals
                        context.add(new AgentMessage(agentSpecification.name(), agentMessages.get(agentMessages.size() - 1)));
                        return;
                    }
                }
            }
        }
        if (agentSpecification.description() != null && !agentSpecification.description().isBlank()) {
            context.add(new AgentMessage(agentSpecification.name(), new UserMessage(agentSpecification.description())));
            context.add(new AgentMessage(agentSpecification.name(), AiMessage.aiMessage(response.toString())));
        }
    }

    public List<AgentMessage> context() {
        return context;
    }

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
        LOG.info("Cognisphere context as conversation: '{}'", contextAsConversation);
        return contextAsConversation;
    }

    public List<AgentCall> getAgentInvocations(String agentName) {
        return agentsInvocations.getOrDefault(agentName, List.of());
    }

    @Override
    public String toString() {
        return "Cognisphere{" +
                "id='" + key.id() + '\'' +
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

    Cognisphere normalizeAfterDeserialization() {
        Map modifiedEntries = new HashMap<>();
        for (Map.Entry<String, Object> entry : state.entrySet()) {
            enumValue(entry).ifPresent(enumValue -> modifiedEntries.put(entry.getKey(), enumValue));
        }
        state.putAll(modifiedEntries);
        return this;
    }

    private Optional<Object> enumValue(Map.Entry<String, Object> entry) {
        if (entry.getValue() instanceof Map m && m.size() == 1) {
            Map.Entry e = (Map.Entry) m.entrySet().iterator().next();
            try {
                Class c = Class.forName(e.getKey().toString());
                if (c.isEnum()) {
                    return Optional.ofNullable(Enum.valueOf(c, e.getValue().toString()));
                }
            } catch (Exception ex) {
                // Ignore
            }
        }
        return Optional.empty();
    }
}
