package dev.langchain4j.model.spark.chat.entity.request.function;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SparkFunctionBuilder {

    private final SparkRequestFunctionMessage sparkRequestFunctionMessage;

    public SparkFunctionBuilder() {
        sparkRequestFunctionMessage = new SparkRequestFunctionMessage();
        sparkRequestFunctionMessage.setParameters(new SparkRequestFunctionParameters("object", new LinkedHashMap<>(), new ArrayList<>()));
    }

    public static SparkFunctionBuilder functionName(String name) {
        return new SparkFunctionBuilder().name(name);
    }

    /**
     * 必传；function名称；用户输入命中后，会返回该名称
     */
    public SparkFunctionBuilder name(String name) {
        sparkRequestFunctionMessage.setName(name);
        return this;
    }

    /**
     * 必传；function功能描述；描述function功能即可，越详细越有助于大模型理解该function
     */
    public SparkFunctionBuilder description(String description) {
        sparkRequestFunctionMessage.setDescription(description);
        return this;
    }

    /**
     * 必传；参数类型;默认值：object
     */
    public SparkFunctionBuilder parameterType(String type) {
        sparkRequestFunctionMessage.getParameters().setType(type);
        return this;
    }

    /**
     * 必传；参数信息描述；该内容由用户定义，命中该方法时需要返回哪些参数
     *
     * @param name        参数名称
     * @param type        参数类型
     * @param description 参数信息描述
     */
    public SparkFunctionBuilder addParameterProperty(String name, String type, String description) {
        sparkRequestFunctionMessage.getParameters().getProperties().put(name, new SparkRequestFunctionProperty(type, description));
        return this;
    }

    /**
     * 必须返回的参数列表
     */
    public SparkFunctionBuilder addParameterRequired(String... name) {
        for (String s : name) {
            sparkRequestFunctionMessage.getParameters().getRequired().add(s);
        }
        return this;
    }

    public SparkFunctionBuilder parameters(String type, Map<String, SparkRequestFunctionProperty> properties, List<String> required) {
        sparkRequestFunctionMessage.setParameters(new SparkRequestFunctionParameters(type, properties, required));
        return this;
    }

    public SparkRequestFunctionMessage build() {
        return sparkRequestFunctionMessage;
    }
}
