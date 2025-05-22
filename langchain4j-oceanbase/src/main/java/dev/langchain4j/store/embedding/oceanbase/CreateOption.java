package dev.langchain4j.store.embedding.oceanbase;

/**
 * Option for creating a table.
 */
public enum CreateOption {
    /**
     * Do not create a table. This assumes that the table already exists.
     */
    CREATE_NONE,

    /**
     * Create the table if it does not exist.
     */
    CREATE_IF_NOT_EXISTS,

    /**
     * Create the table, replacing an existing table if it exists.
     */
    CREATE_OR_REPLACE
}
