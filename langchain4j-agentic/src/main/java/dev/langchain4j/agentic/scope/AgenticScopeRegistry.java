package dev.langchain4j.agentic.scope;

import dev.langchain4j.Internal;
import dev.langchain4j.agentic.observability.AgentListener;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import java.util.Set;

import static dev.langchain4j.agentic.observability.ListenerNotifierUtil.beforeAgenticScopeDestroyed;

/**
 * Singleton registry for managing AgenticScope instances.
 * Provides methods to register, retrieve, and manage AgenticScope objects.
 * Supports persistence through a pluggable store.
 */
@Internal
public class AgenticScopeRegistry {

    private final String agentId;
    private final AgenticScopeStore store;

    private final Map<AgenticScopeKey, DefaultAgenticScope> inMemoryAgenticScope = new ConcurrentHashMap<>();

    public AgenticScopeRegistry(String agentId) {
        this.agentId = agentId;
        this.store = AgenticScopePersister.store;
    }

    private boolean hasStore() {
        return store != null;
    }

    public void update(DefaultAgenticScope agenticScope) {
        if (hasStore()) {
            store.save(new AgenticScopeKey(agentId, agenticScope.memoryId()), agenticScope);
        }
    }

    public DefaultAgenticScope get(Object memoryId) {
        AgenticScopeKey key = new AgenticScopeKey(agentId, memoryId);
        DefaultAgenticScope agenticScope = inMemoryAgenticScope.get(key);
        if (agenticScope == null && hasStore()) {
            agenticScope = store.load(key)
                    .map(loaded -> {
                        inMemoryAgenticScope.put(key, loaded);
                        return loaded;
                    }).orElse(null);
        }
        return agenticScope;
    }

    public DefaultAgenticScope create(Object memoryId) {
        DefaultAgenticScope agenticScope = new DefaultAgenticScope(memoryId, hasStore() ? DefaultAgenticScope.Kind.PERSISTENT : DefaultAgenticScope.Kind.REGISTERED);
        register(agenticScope);
        return agenticScope;
    }

    public DefaultAgenticScope createEphemeralAgenticScope() {
        DefaultAgenticScope agenticScope = new DefaultAgenticScope(DefaultAgenticScope.Kind.EPHEMERAL);
        register(agenticScope);
        return agenticScope;
    }

    private void register(DefaultAgenticScope agenticScope) {
        inMemoryAgenticScope.put(new AgenticScopeKey(agentId, agenticScope.memoryId()), agenticScope);
        update(agenticScope);
    }

    public boolean evict(Object memoryId, AgentListener listener) {
        AgenticScopeKey key = new AgenticScopeKey(agentId, memoryId);
        DefaultAgenticScope agenticScope = inMemoryAgenticScope.remove(key);
        boolean removed = agenticScope != null;
        if (removed) {
            beforeAgenticScopeDestroyed(listener, agenticScope);
        }
        if (hasStore()) {
            return store.delete(key) || removed;
        }
        return removed;
    }

    public Set<AgenticScopeKey> getAllAgenticScopeKeysInMemory() {
        return inMemoryAgenticScope.keySet();
    }

    public void clearInMemory() {
        inMemoryAgenticScope.clear();
    }
}
