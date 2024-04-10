package dev.langchain4j.model.spark.chat.entity.request;

import dev.langchain4j.model.spark.chat.entity.SparkRequestBuilder;
import lombok.Data;

@Data
public class SparkRequest {

    private SparkRequestHeader header;

    private  SparkRequestParameter parameter;

    private  SparkRequestPayload payload;

    public static SparkRequestBuilder builder() {
        return new SparkRequestBuilder();
    }
}
