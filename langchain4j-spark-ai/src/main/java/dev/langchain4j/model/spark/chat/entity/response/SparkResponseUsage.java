package dev.langchain4j.model.spark.chat.entity.response;

import lombok.Data;

import java.io.Serializable;

@Data
public class SparkResponseUsage {
    private SparkTextUsage text;
}
