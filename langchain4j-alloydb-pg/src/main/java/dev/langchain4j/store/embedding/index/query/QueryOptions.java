package dev.langchain4j.store.embedding.index.query;

import java.util.List;

/**
 * Query options interface
 */
public interface QueryOptions {

    /**
     * Convert index attributes to list of configuration
     * @return List of parameter setting strings
     */
    public List<String> getParameterSettings();
}
