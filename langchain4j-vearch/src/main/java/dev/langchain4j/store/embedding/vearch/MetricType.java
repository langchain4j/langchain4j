package dev.langchain4j.store.embedding.vearch;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * if metric type is not set when searching, it will use the parameter specified when building the space
 *
 * <p>LangChain4j currently only support {@link MetricType#INNER_PRODUCT}</p>
 */
public enum MetricType {

    /**
     * Inner Product
     */
    @JsonProperty("InnerProduct")
    INNER_PRODUCT
}
