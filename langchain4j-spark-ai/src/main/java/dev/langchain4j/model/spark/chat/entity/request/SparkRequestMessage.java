package dev.langchain4j.model.spark.chat.entity.request;


import dev.langchain4j.model.spark.chat.entity.SparkMessage;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class SparkRequestMessage {
    private List<SparkMessage> text;
}
