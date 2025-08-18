package dev.langchain4j.agentic.scope;

import dev.langchain4j.Internal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import java.util.Set;

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

    public DefaultAgenticScope getOrCreate(Object memoryId) {
        DefaultAgenticScope agenticScope = get(memoryId);
        if (agenticScope == null) {
            agenticScope = new DefaultAgenticScope(memoryId, hasStore() ? DefaultAgenticScope.Kind.PERSISTENT : DefaultAgenticScope.Kind.REGISTERED);
            register(agenticScope);
        }
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

    public boolean evict(Object memoryId) {
        AgenticScopeKey key = new AgenticScopeKey(agentId, memoryId);
        boolean removed = inMemoryAgenticScope.remove(key) != null;
        if (hasStore()) {
            return store.delete(key) || removed;
        }
        return removed;
    }

    public Set<AgenticScopeKey> getAllAgenticScopeKeys() {
        if (hasStore()) {
            return store.getAllKeys();
        }
        return getAllAgenticScopeKeysInMemory();
    }

    public Set<AgenticScopeKey> getAllAgenticScopeKeysInMemory() {
        return inMemoryAgenticScope.keySet();
    }

    public void clearInMemory() {
        inMemoryAgenticScope.clear();
    }
}
