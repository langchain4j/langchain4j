package dev.langchain4j.store.embedding.vearch.api.space;

/**
 * if metric type is not set when searching, it will use the parameter specified when building the space
 */
public enum MetricType {

    INNER_PRODUCT("InnerProduct"),
    L2("L2");

    private final String name;

    MetricType(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
