package dev.langchain4j.model.spark.chat.entity.response;

import lombok.Data;

import java.io.Serializable;


@Data
public class SparkResponse  {

    private SparkResponseHeader header;

    private SparkResponsePayload payload;
}
