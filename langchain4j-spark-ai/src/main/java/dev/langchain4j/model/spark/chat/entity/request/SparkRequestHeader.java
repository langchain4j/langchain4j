package dev.langchain4j.model.spark.chat.entity.request;


import com.alibaba.fastjson.annotation.JSONField;
import lombok.Data;

import java.io.Serializable;


@Data
public class SparkRequestHeader {
    /**
     * 应用appid，从开放平台控制台创建的应用中获取<br/>
     * 必传
     */
    @JSONField(name = "app_id")
    private String appId;

    /**
     * 每个用户的id，用于区分不同用户<br/>
     * 非必传，最大长度32
     */
    private String uid;

}
