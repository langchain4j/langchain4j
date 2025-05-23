package dev.langchain4j.agentic.cognisphere;

import java.util.ServiceLoader;

public enum CognispherePersister {

    INSTANCE;

    CognispherePersister() {
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
    public static void setPersistenceProvider(CognispherePersistenceProvider provider) {
        CognisphereRegistry.setPersistenceProvider(provider);
    }
}
