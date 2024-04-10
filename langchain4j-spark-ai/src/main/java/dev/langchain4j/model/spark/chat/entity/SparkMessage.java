package dev.langchain4j.model.spark.chat.entity;


import com.alibaba.fastjson.annotation.JSONField;
import dev.langchain4j.model.spark.chat.constant.SparkMessageRole;
import dev.langchain4j.model.spark.chat.entity.response.SparkResponseFunctionCall;
import lombok.Data;


@Data
public class SparkMessage {

    /**
     * 角色
     */
    private String role;

    /**
     * 内容类型
     */
    @JSONField(name="content_type")
    private String contentType;

    /**
     * 函数调用
     */
    @JSONField(name="function_call")
    private SparkResponseFunctionCall functionCall;

    /**
     * 内容
     */
    private String content;

    /**
     * 响应时独有，请求入参请忽略
     */
    private String index;

    /**
     * 创建用户消息
     *
     * @param content 内容
     */
    public static SparkMessage userContent(String content) {
        return new SparkMessage(SparkMessageRole.USER, content);
    }

    /**
     * 创建机器人消息
     *
     * @param content 内容
     */
    public static SparkMessage assistantContent(String content) {
        return new SparkMessage(SparkMessageRole.ASSISTANT, content);
    }

    /**
     * 创建system指令
     * @param content 内容
     */
    public static SparkMessage systemContent(String content) {
        return new SparkMessage(SparkMessageRole.SYSTEM, content);
    }

    public SparkMessage() {
    }

    public SparkMessage(String role, String content) {
        this.role = role;
        this.content = content;
    }


}
