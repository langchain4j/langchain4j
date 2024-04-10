package dev.langchain4j.model.spark.chat.entity.response;

import lombok.Data;

import java.io.Serializable;


@Data
public class SparkResponsePayload{
    private SparkResponseChoices choices;
    private SparkResponseUsage usage;
}
