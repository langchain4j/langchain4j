package dev.langchain4j.agentic.scope;

import java.util.ServiceLoader;

public enum AgenticScopePersister {

    INSTANCE;

    static AgenticScopeStore store;

    AgenticScopePersister() {
        setStore(loadStore());
    }

    private static AgenticScopeStore loadStore() {
        ServiceLoader<AgenticScopeStore> loader =
                ServiceLoader.load(AgenticScopeStore.class);

        for (AgenticScopeStore provider : loader) {
            return provider; // Return the first provider found
        }
        return null; // No provider found
    }

    /**
     * Explicitly set a persistence provider.
     */
    public static void setStore(AgenticScopeStore store) {
        AgenticScopePersister.store = store;
    }
}
