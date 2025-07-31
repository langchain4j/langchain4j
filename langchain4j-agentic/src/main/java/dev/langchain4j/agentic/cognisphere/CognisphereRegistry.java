package dev.langchain4j.agentic.cognisphere;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import java.util.ServiceLoader;
import java.util.Set;

/**
 * Singleton registry for managing Cognisphere instances.
 * Provides methods to register, retrieve, and manage Cognisphere objects.
 * Supports persistence through a pluggable persistence provider.
 */
public enum CognisphereRegistry {

    INSTANCE;

    private final Map<Object, Cognisphere> inMemoryCognispheres = new ConcurrentHashMap<>();
    private CognispherePersistenceProvider persistenceProvider;

    CognisphereRegistry() {
        internalSetPersistenceProvider(loadPersistenceProvider());
    }

    private static CognispherePersistenceProvider loadPersistenceProvider() {
        ServiceLoader<CognispherePersistenceProvider> loader =
                ServiceLoader.load(CognispherePersistenceProvider.class);

        for (CognispherePersistenceProvider provider : loader) {
            return provider; // Return the first provider found
        }
        return null; // No provider found
    }

    /**
     * Explicitly set a persistence provider.
     */
    public static void setPersistenceProvider(CognispherePersistenceProvider provider) {
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
        return INSTANCE.internalGet(id);
    }

    private Cognisphere internalGet(Object id) {
        Cognisphere cognisphere = inMemoryCognispheres.get(id);
        if (cognisphere == null && hasPersistenceProvider()) {
            cognisphere = persistenceProvider.load(id).map(loaded -> {
                inMemoryCognispheres.put(id, loaded);
                return loaded;
            }).orElse(null);
        }
        return cognisphere;
    }

    public static Cognisphere getOrCreate(Object id) {
        return INSTANCE.internalGetOrCreate(id);
    }

    public Cognisphere internalGetOrCreate(Object id) {
        Cognisphere cognisphere = get(id);
        if (cognisphere == null) {
            cognisphere = new Cognisphere(id, hasPersistenceProvider() ? Cognisphere.Kind.PERSISTENT : Cognisphere.Kind.REGISTERED);
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
        inMemoryCognispheres.put(cognisphere.id(), cognisphere);
        update(cognisphere);
    }

    public static boolean evict(Object id) {
        return INSTANCE.internalEvict(id);
    }

    public boolean internalEvict(Object id) {
        boolean removed = inMemoryCognispheres.remove(id) != null;
        if (hasPersistenceProvider()) {
            return persistenceProvider.delete(id) || removed;
        }
        return removed;
    }

    public static Set<Object> getAllIds() {
        return INSTANCE.internalGetAllIds();
    }

    private Set<Object> internalGetAllIds() {
        if (hasPersistenceProvider()) {
            return persistenceProvider.getAllIds();
        }
        return getAllIdsInMemory();
    }

    public static Set<Object> getAllIdsInMemory() {
        return INSTANCE.internalGetAllIdsInMemory();
    }

    private Set<Object> internalGetAllIdsInMemory() {
        return inMemoryCognispheres.keySet();
    }

    public static void clearInMemory() {
        INSTANCE.internalClearInMemory();
    }

    private void internalClearInMemory() {
        inMemoryCognispheres.clear();
    }
}
