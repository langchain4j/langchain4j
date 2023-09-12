package dev.langchain4j.store.embedding.redis;

/**
 * Redis vector field data type
 */
public enum DataType {

    /**
     * only support FLOAT32
     */
    FLOAT32("FLOAT32"),
    FLOAT64("FLOAT64");

    private final String name;

    DataType(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
