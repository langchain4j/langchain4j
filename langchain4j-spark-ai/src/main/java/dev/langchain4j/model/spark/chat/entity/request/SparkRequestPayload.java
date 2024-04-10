package dev.langchain4j.model.spark.chat.entity.request;


import dev.langchain4j.model.spark.chat.entity.request.function.SparkRequestFunctions;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class SparkRequestPayload  {
    private SparkRequestMessage message;
    private SparkRequestFunctions functions;

    public SparkRequestPayload(SparkRequestMessage message) {
        this.message = message;
    }

}
