package dev.langchain4j.model.spark.chat.entity.response;


import com.alibaba.fastjson.annotation.JSONField;
import lombok.Data;

import java.io.Serializable;


@Data
public class SparkTextUsage  {
    /**
     * 包含历史问题的总tokens大小(提问tokens大小)
     */
    @JSONField(name="prompt_tokens")
    private Integer promptTokens;

    /**
     * 回答的tokens大小
     */
    @JSONField(name ="completion_tokens")
    private Integer completionTokens;

    /**
     * prompt_tokens和completion_tokens的和，也是本次交互计费的tokens大小
     */
    @JSONField(name ="total_tokens")
    private Integer totalTokens;

    /**
     * 保留字段，可忽略
     */
    @JSONField(name ="question_tokens")
    private Integer questionTokens;

}
