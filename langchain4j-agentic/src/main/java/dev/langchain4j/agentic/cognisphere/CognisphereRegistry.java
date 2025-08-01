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
public enum CognisphereRegistry {

    INSTANCE;

    private final Map<CognisphereKey, DefaultCognisphere> inMemoryCognispheres = new ConcurrentHashMap<>();
    private CognisphereStore store;

    private final ThreadLocal<String> currentAgentId = new ThreadLocal<>();

    static void setCurrentAgentId(String agentId) {
        INSTANCE.internalSetCurrentAgentId(agentId);
    }

    private void internalSetCurrentAgentId(String agentId) {
        currentAgentId.set(agentId);
    }

    static void resetCurrentAgentId() {
        INSTANCE.internalResetCurrentAgentId();
    }

    private void internalResetCurrentAgentId() {
        currentAgentId.remove();
    }

    /**
     * Explicitly set a store.
     */
    static void setStore(CognisphereStore store) {
        INSTANCE.internalSetStore(store);
    }

    private void internalSetStore(CognisphereStore store) {
        if (!inMemoryCognispheres.isEmpty()) {
            throw new IllegalStateException("Cannot set a store on an already populated CognisphereRegistry.");
        }
        this.store = store;
    }

    public static boolean hasStore() {
        return INSTANCE.internalHasStore();
    }

    private boolean internalHasStore() {
        return store != null;
    }

    static void update(DefaultCognisphere cognisphere) {
        INSTANCE.internalUpdate(cognisphere);
    }

    private void internalUpdate(DefaultCognisphere cognisphere) {
        if (hasStore()) {
            store.save(cognisphere);
        }
    }

    public static DefaultCognisphere get(Object id) {
        return get(new CognisphereKey(INSTANCE.currentAgentId.get(), id));
    }

    public static DefaultCognisphere get(CognisphereKey key) {
        return INSTANCE.internalGet(key);
    }

    private DefaultCognisphere internalGet(CognisphereKey key) {
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

    public static DefaultCognisphere getOrCreate(CognisphereKey key) {
        return INSTANCE.internalGetOrCreate(key);
    }

    public DefaultCognisphere internalGetOrCreate(CognisphereKey key) {
        DefaultCognisphere cognisphere = get(key);
        if (cognisphere == null) {
            cognisphere = new DefaultCognisphere(key, hasStore() ? DefaultCognisphere.Kind.PERSISTENT : DefaultCognisphere.Kind.REGISTERED);
            register(cognisphere);
        }
        return cognisphere;
    }

    public static DefaultCognisphere createEphemeralCognisphere() {
        return INSTANCE.internalCreateEphemeralCognisphere();
    }

    private DefaultCognisphere internalCreateEphemeralCognisphere() {
        DefaultCognisphere cognisphere = new DefaultCognisphere(DefaultCognisphere.Kind.EPHEMERAL);
        register(cognisphere);
        return cognisphere;
    }

    private void register(DefaultCognisphere cognisphere) {
        inMemoryCognispheres.put(cognisphere.key(), cognisphere);
        update(cognisphere);
    }

    public static boolean evict(CognisphereKey key) {
        return INSTANCE.internalEvict(key);
    }

    public boolean internalEvict(CognisphereKey key) {
        boolean removed = inMemoryCognispheres.remove(key) != null;
        if (hasStore()) {
            return store.delete(key) || removed;
        }
        return removed;
    }

    public static Set<CognisphereKey> getAllCognisphereKeys() {
        return INSTANCE.internalGetAllCognisphereKeys();
    }

    private Set<CognisphereKey> internalGetAllCognisphereKeys() {
        if (hasStore()) {
            return store.getAllKeys();
        }
        return getAllCognisphereKeysInMemory();
    }

    public static Set<CognisphereKey> getAllCognisphereKeysInMemory() {
        return INSTANCE.internalGetAllCognisphereKeysInMemory();
    }

    private Set<CognisphereKey> internalGetAllCognisphereKeysInMemory() {
        return inMemoryCognispheres.keySet();
    }

    public static void clearInMemory() {
        INSTANCE.internalClearInMemory();
    }

    private void internalClearInMemory() {
        inMemoryCognispheres.clear();
    }
}
