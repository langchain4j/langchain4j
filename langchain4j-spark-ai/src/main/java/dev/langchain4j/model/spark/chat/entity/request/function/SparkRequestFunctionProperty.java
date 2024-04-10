package dev.langchain4j.model.spark.chat.entity.request.function;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SparkRequestFunctionProperty  {
    /**
     * 必传；参数信息描述；该内容由用户定义，需要返回的参数是什么类型
     */
    private String type;

    /**
     * 必传；参数详细描述；该内容由用户定义，需要返回的参数的具体描述
     */
    private String description;

}
