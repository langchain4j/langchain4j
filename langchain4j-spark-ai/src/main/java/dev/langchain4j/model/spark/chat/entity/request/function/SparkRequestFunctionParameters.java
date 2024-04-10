package dev.langchain4j.model.spark.chat.entity.request.function;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.Map;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class SparkRequestFunctionParameters {

    /**
     * 必传；参数类型
     */
    private String type;
    /**
     * 必传；参数信息描述；该内容由用户定义，命中该方法时需要返回哪些参数<br/>
     * key：参数名称<br/>
     * value：参数信息描述
     */
    private Map<String,SparkRequestFunctionProperty> properties;
    /**
     * 必传；必须返回的参数列表；该内容由用户定义，命中方法时必须返回的字段;properties中的key
     */
    private List<String> required;

}
