package dev.langchain4j.store.embedding.redis;

/**
 * Redis vector field distance Metric
 */
public enum MetricType {

    COSINE("COSINE"),
    IP("IP"),
    L2("L2");

    private final String name;

    MetricType(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
