package dev.langchain4j.model.spark.chat.entity.response;




import lombok.Data;
import java.io.Serializable;


@Data
public class SparkResponseFunctionCall {

    private String arguments;
    private String name;
}
