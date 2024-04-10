package dev.langchain4j.model.spark.chat.entity.response;


import dev.langchain4j.model.spark.chat.entity.SparkMessage;
import lombok.Data;

import java.io.Serializable;
import java.util.List;


@Data
public class SparkResponseChoices  {

    /**
     * 文本响应状态，取值为[0,1,2]; 0代表首个文本结果；1代表中间文本结果；2代表最后一个文本结果
     */
    private Integer status;

    /**
     * 返回的数据序号，取值为[0,9999999]
     */
    private Integer seq;

    /**
     * 消息列表
     */
    private List<SparkMessage> text;

}
