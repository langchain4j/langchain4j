package dev.langchain4j.agentic.cognisphere;

import dev.langchain4j.agentic.Cognisphere;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import java.util.ServiceLoader;
import java.util.Set;

/**
 * Singleton registry for managing Cognisphere instances.
 * Provides methods to register, retrieve, and manage Cognisphere objects.
 * Supports persistence through a pluggable persistence provider.
 */
public class CognisphereRegistry {

    private static final CognisphereRegistry INSTANCE = new CognisphereRegistry();

    private final Map<Object, Cognisphere> inMemoryCognispheres = new ConcurrentHashMap<>();
    private CognispherePersistenceProvider persistenceProvider;

    public static CognisphereRegistry getInstance() {
        return INSTANCE;
    }

    private CognisphereRegistry() {
        setPersistenceProvider(loadPersistenceProvider());
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
    public void setPersistenceProvider(CognispherePersistenceProvider provider) {
        this.persistenceProvider = provider;
    }

    public boolean hasPersistenceProvider() {
        return persistenceProvider != null;
    }

    public void register(Cognisphere cognisphere) {
        inMemoryCognispheres.put(cognisphere.id(), cognisphere);
        update(cognisphere);
    }

    public void update(Cognisphere cognisphere) {
        if (hasPersistenceProvider()) {
            persistenceProvider.save(cognisphere);
        }
    }

    public Cognisphere get(Object id) {
        Cognisphere cognisphere = inMemoryCognispheres.get(id);
        if (cognisphere == null && hasPersistenceProvider()) {
            cognisphere = persistenceProvider.load(id).map(loaded -> {
                inMemoryCognispheres.put(id, loaded);
                return loaded;
            }).orElse(null);
        }
        return cognisphere;
    }

    public Cognisphere getOrCreate(Object id) {
        Cognisphere cognisphere = get(id);
        if (cognisphere == null) {
            cognisphere = new Cognisphere(id);
            register(cognisphere);
        }
        return cognisphere;
    }

    public boolean evict(Object id) {
        boolean removed = inMemoryCognispheres.remove(id) != null;
        if (hasPersistenceProvider()) {
            return persistenceProvider.delete(id) || removed;
        }
        return removed;
    }

    public void clear() {
        inMemoryCognispheres.clear();
        // Note: This doesn't clear the persistent storage
    }

    public Set<Object> getAllIds() {
        if (hasPersistenceProvider()) {
            return persistenceProvider.getAllIds();
        }
        return inMemoryCognispheres.keySet();
    }
}
