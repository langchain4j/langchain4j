package dev.langchain4j.agentic.cognisphere;

import dev.langchain4j.Internal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import java.util.Set;

/**
 * Singleton registry for managing Cognisphere instances.
 * Provides methods to register, retrieve, and manage Cognisphere objects.
 * Supports persistence through a pluggable store.
 */
@Internal
public class CognisphereRegistry {

    private final String agentId;
    private final CognisphereStore store;

    private final Map<CognisphereKey, DefaultCognisphere> inMemoryCognispheres = new ConcurrentHashMap<>();

    public CognisphereRegistry(String agentId) {
        this.agentId = agentId;
        this.store = CognispherePersister.store;
    }

    private boolean hasStore() {
        return store != null;
    }

    public void update(DefaultCognisphere cognisphere) {
        if (hasStore()) {
            store.save(new CognisphereKey(agentId, cognisphere.memoryId()), cognisphere);
        }
    }

    public DefaultCognisphere get(Object memoryId) {
        CognisphereKey key = new CognisphereKey(agentId, memoryId);
        DefaultCognisphere cognisphere = inMemoryCognispheres.get(key);
        if (cognisphere == null && hasStore()) {
            cognisphere = store.load(key)
                    .map(loaded -> {
                        inMemoryCognispheres.put(key, loaded);
                        return loaded;
                    }).orElse(null);
        }
        return cognisphere;
    }

    public DefaultCognisphere getOrCreate(Object memoryId) {
        DefaultCognisphere cognisphere = get(memoryId);
        if (cognisphere == null) {
            cognisphere = new DefaultCognisphere(memoryId, hasStore() ? DefaultCognisphere.Kind.PERSISTENT : DefaultCognisphere.Kind.REGISTERED);
            register(cognisphere);
        }
        return cognisphere;
    }

    public DefaultCognisphere createEphemeralCognisphere() {
        DefaultCognisphere cognisphere = new DefaultCognisphere(DefaultCognisphere.Kind.EPHEMERAL);
        register(cognisphere);
        return cognisphere;
    }

    private void register(DefaultCognisphere cognisphere) {
        inMemoryCognispheres.put(new CognisphereKey(agentId, cognisphere.memoryId()), cognisphere);
        update(cognisphere);
    }

    public boolean evict(Object memoryId) {
        CognisphereKey key = new CognisphereKey(agentId, memoryId);
        boolean removed = inMemoryCognispheres.remove(key) != null;
        if (hasStore()) {
            return store.delete(key) || removed;
        }
        return removed;
    }

    public Set<CognisphereKey> getAllCognisphereKeys() {
        if (hasStore()) {
            return store.getAllKeys();
        }
        return getAllCognisphereKeysInMemory();
    }

    public Set<CognisphereKey> getAllCognisphereKeysInMemory() {
        return inMemoryCognispheres.keySet();
    }

    public void clearInMemory() {
        inMemoryCognispheres.clear();
    }
}
