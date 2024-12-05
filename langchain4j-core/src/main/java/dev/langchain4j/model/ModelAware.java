package dev.langchain4j.model;

/**
 * A simple interface marking the model implementation being model aware; e.g. name.
 * This exposes some of the implementation details in more type-safe way.
 */
public interface ModelAware {
    String modelName();
}
