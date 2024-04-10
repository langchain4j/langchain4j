package dev.langchain4j.model.spark.chat.entity;

import com.alibaba.fastjson.annotation.JSONField;
import dev.langchain4j.model.spark.chat.entity.response.SparkResponseFunctionCall;
import dev.langchain4j.model.spark.chat.entity.response.SparkTextUsage;
import lombok.Data;

import java.io.Serializable;


@Data
public class SparkSyncChatResponse  {

    /**
     * 回答内容
     */
    private String content;

    private SparkResponseFunctionCall functionCall;

    /**
     * tokens统计
     */
    private SparkTextUsage textUsage;

    /**
     * 内部业务逻辑自用字段
     */
    @JSONField(serialize=false)
    private boolean ok = false;

    private Throwable exception;

}
