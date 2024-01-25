package dev.langchain4j.store.embedding.vearch;

import com.google.gson.annotations.SerializedName;

/**
 * if metric type is not set when searching, it will use the parameter specified when building the space
 *
 * <p>LangChain4j currently only support {@link MetricType#INNER_PRODUCT}</p>
 */
public enum MetricType {

    /**
     * Inner Product
     */
    @SerializedName("InnerProduct")
    INNER_PRODUCT
}
