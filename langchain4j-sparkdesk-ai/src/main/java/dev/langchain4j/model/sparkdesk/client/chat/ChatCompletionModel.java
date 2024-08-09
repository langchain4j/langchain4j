package dev.langchain4j.model.sparkdesk.client.chat;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ChatCompletionModel {
    SPARK_LITE("general", "wss://spark-api.xf-yun.com/v1.1/chat"),
    SPARK_V2("generalv2", "wss://spark-api.xf-yun.com/v2.1/chat"),
    SPARK_PRO("generalv3", "wss://spark-api.xf-yun.com/v3.1/chat"),
    SPARK_MAX("generalv3.5", "wss://spark-api.xf-yun.com/v3.5/chat"),
    SPARK_ULTRA("4.0Ultra", "wss://spark-api.xf-yun.com/v4.0/chat");

    private final String domain;
    private final String wssEndpoint;

    ChatCompletionModel(String domain, String wssEndpoint) {
        this.domain = domain;
        this.wssEndpoint = wssEndpoint;
    }

    public String getWssEndpoint() {
        return this.wssEndpoint;
    }

    @Override
    @JsonValue
    public String toString() {
        return this.domain;
    }
}
