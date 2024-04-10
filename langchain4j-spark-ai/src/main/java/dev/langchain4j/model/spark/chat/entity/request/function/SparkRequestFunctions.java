package dev.langchain4j.model.spark.chat.entity.request.function;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SparkRequestFunctions  {
    private List<SparkRequestFunctionMessage> text;
}
