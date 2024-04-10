package dev.langchain4j.model.spark.chat.entity.request.function;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class SparkRequestFunctionMessage  {
    /**
     * 必传；function名称；用户输入命中后，会返回该名称
     */
    private String name;
    /**
     * 必传；function功能描述；描述function功能即可，越详细越有助于大模型理解该function
     */
    private String description;
    /**
     * 必传；function参数列表
     */
    private SparkRequestFunctionParameters parameters;

}
