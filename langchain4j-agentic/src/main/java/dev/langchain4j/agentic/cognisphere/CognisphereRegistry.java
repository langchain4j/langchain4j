package dev.langchain4j.agentic.cognisphere;

import dev.langchain4j.Internal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import java.util.ServiceLoader;
import java.util.Set;

/**
 * Singleton registry for managing Cognisphere instances.
 * Provides methods to register, retrieve, and manage Cognisphere objects.
 * Supports persistence through a pluggable persistence provider.
 */
@Internal
public enum CognisphereRegistry {

    INSTANCE;

    private final Map<CognisphereKey, Cognisphere> inMemoryCognispheres = new ConcurrentHashMap<>();
    private CognispherePersistenceProvider persistenceProvider;

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
     * Explicitly set a persistence provider.
     */
    static void setPersistenceProvider(CognispherePersistenceProvider provider) {
        INSTANCE.internalSetPersistenceProvider(provider);
    }

    private void internalSetPersistenceProvider(CognispherePersistenceProvider provider) {
        if (!inMemoryCognispheres.isEmpty()) {
            throw new IllegalStateException("Cannot set a persistence provider on an already populated CognisphereRegistry.");
        }
        this.persistenceProvider = provider;
    }

    public static boolean hasPersistenceProvider() {
        return INSTANCE.internalHasPersistenceProvider();
    }

    private boolean internalHasPersistenceProvider() {
        return persistenceProvider != null;
    }

    static void update(Cognisphere cognisphere) {
        INSTANCE.internalUpdate(cognisphere);
    }

    private void internalUpdate(Cognisphere cognisphere) {
        if (hasPersistenceProvider()) {
            persistenceProvider.save(cognisphere);
        }
    }

    public static Cognisphere get(Object id) {
        return get(new CognisphereKey(INSTANCE.currentAgentId.get(), id));
    }

    public static Cognisphere get(CognisphereKey key) {
        return INSTANCE.internalGet(key);
    }

    private Cognisphere internalGet(CognisphereKey key) {
        Cognisphere cognisphere = inMemoryCognispheres.get(key);
        if (cognisphere == null && hasPersistenceProvider()) {
            cognisphere = persistenceProvider.load(key).map(loaded -> {
                inMemoryCognispheres.put(key, loaded);
                return loaded;
            }).orElse(null);
        }
        return cognisphere;
    }

    public static Cognisphere getOrCreate(CognisphereKey key) {
        return INSTANCE.internalGetOrCreate(key);
    }

    public Cognisphere internalGetOrCreate(CognisphereKey key) {
        Cognisphere cognisphere = get(key);
        if (cognisphere == null) {
            cognisphere = new Cognisphere(key, hasPersistenceProvider() ? Cognisphere.Kind.PERSISTENT : Cognisphere.Kind.REGISTERED);
            register(cognisphere);
        }
        return cognisphere;
    }

    public static Cognisphere createEphemeralCognisphere() {
        return INSTANCE.internalCreateEphemeralCognisphere();
    }

    private Cognisphere internalCreateEphemeralCognisphere() {
        Cognisphere cognisphere = new Cognisphere(Cognisphere.Kind.EPHEMERAL);
        register(cognisphere);
        return cognisphere;
    }

    private void register(Cognisphere cognisphere) {
        inMemoryCognispheres.put(cognisphere.key(), cognisphere);
        update(cognisphere);
    }

    public static boolean evict(CognisphereKey key) {
        return INSTANCE.internalEvict(key);
    }

    public boolean internalEvict(CognisphereKey key) {
        boolean removed = inMemoryCognispheres.remove(key) != null;
        if (hasPersistenceProvider()) {
            return persistenceProvider.delete(key) || removed;
        }
        return removed;
    }

    public static Set<CognisphereKey> getAllCognisphereKeys() {
        return INSTANCE.internalGetAllCognisphereKeys();
    }

    private Set<CognisphereKey> internalGetAllCognisphereKeys() {
        if (hasPersistenceProvider()) {
            return persistenceProvider.getAllIds();
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
