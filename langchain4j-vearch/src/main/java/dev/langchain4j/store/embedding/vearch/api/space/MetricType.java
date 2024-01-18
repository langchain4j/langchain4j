package dev.langchain4j.store.embedding.vearch.api.space;

import com.google.gson.annotations.SerializedName;

/**
 * if metric type is not set when searching, it will use the parameter specified when building the space
 */
public enum MetricType {

    /**
     * Inner Product
     */
    @SerializedName("InnerProduct")
    INNER_PRODUCT,
    /**
     * L2
     */
    L2
}
