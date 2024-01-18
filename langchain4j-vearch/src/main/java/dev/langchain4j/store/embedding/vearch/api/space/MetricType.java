package dev.langchain4j.store.embedding.vearch.api.space;

import lombok.Getter;

/**
 * if metric type is not set when searching, it will use the parameter specified when building the space
 */
public enum MetricType {

    /**
     * Inner Product
     */
    INNER_PRODUCT("InnerProduct"),
    /**
     * L2
     */
    L2("L2");

    @Getter
    private final String name;

    MetricType(String name) {
        this.name = name;
    }
}
