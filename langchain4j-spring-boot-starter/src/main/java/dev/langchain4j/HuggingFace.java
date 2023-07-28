package dev.langchain4j;

import java.time.Duration;

class HuggingFace {

    private String accessToken;
    private String modelId;
    private Duration timeout;
    private Double temperature;
    private Integer maxNewTokens;
    private Boolean returnFullText;
    private Boolean waitForModel;

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public Integer getMaxNewTokens() {
        return maxNewTokens;
    }

    public void setMaxNewTokens(Integer maxNewTokens) {
        this.maxNewTokens = maxNewTokens;
    }

    public Boolean getReturnFullText() {
        return returnFullText;
    }

    public void setReturnFullText(Boolean returnFullText) {
        this.returnFullText = returnFullText;
    }

    public Boolean getWaitForModel() {
        return waitForModel;
    }

    public void setWaitForModel(Boolean waitForModel) {
        this.waitForModel = waitForModel;
    }
}