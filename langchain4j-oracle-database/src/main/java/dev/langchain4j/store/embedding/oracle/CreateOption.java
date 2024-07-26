package dev.langchain4j.store.embedding.oracle;

/**
 * Options which configure the creation of database schema objects, such as tables and indexes.
 */
public enum CreateOption {

    /** No attempt is made to create the schema object. */
    CREATE_NONE,

    /** An existing schema object is reused, otherwise it is created. */
    CREATE_IF_NOT_EXISTS,

    /** An existing schema object is dropped and replaced with a new one. */
    CREATE_OR_REPLACE
}
