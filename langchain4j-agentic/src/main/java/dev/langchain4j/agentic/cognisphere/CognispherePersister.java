package dev.langchain4j.agentic.cognisphere;

import java.util.ServiceLoader;

public enum CognispherePersister {

    INSTANCE;

    CognispherePersister() {
        setStore(loadStore());
    }

    private static CognisphereStore loadStore() {
        ServiceLoader<CognisphereStore> loader =
                ServiceLoader.load(CognisphereStore.class);

        for (CognisphereStore provider : loader) {
            return provider; // Return the first provider found
        }
        return null; // No provider found
    }

    /**
     * Explicitly set a persistence provider.
     */
    public static void setStore(CognisphereStore provider) {
        CognisphereRegistry.setStore(provider);
    }
}
