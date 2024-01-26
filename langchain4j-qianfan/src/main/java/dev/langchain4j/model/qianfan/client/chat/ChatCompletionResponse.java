package dev.langchain4j.model.qianfan.client.chat;

import dev.langchain4j.model.qianfan.client.Usage;

public final class ChatCompletionResponse {

    private final String id;
    private final Integer errorCode;
    private final String errorMsg;
    private final String object;
    private final Integer created;
    private final Integer sentenceId;
    private final Boolean isEnd;
    private final Boolean isTruncated;
    private final String result;
    private final Boolean needClearHistory;
    private final Integer banRound;
    private final Usage usage;
    private final FunctionCall functionCall;


    private final String finishReason;

    private ChatCompletionResponse(Builder builder) {
        this.id = builder.id;
        this.created = builder.created;
        this.object = builder.object;
        this.sentenceId = builder.sentenceId;
        this.isEnd = builder.isEnd;
        this.isTruncated = builder.isTruncated;
        this.result = builder.result;
        this.needClearHistory = builder.needClearHistory;
        this.banRound = builder.banRound;
        this.functionCall = builder.functionCall;
        this.usage = builder.usage;
        this.errorCode = builder.errorCode;
        this.errorMsg = builder.errorMsg;
        this.finishReason = builder.finishReason;
    }



    @Override
    public String toString() {
        return "ChatCompletionResponse{" +
                "id='" + id + '\'' +
                ", errorCode=" + errorCode +
                ", errorMsg='" + errorMsg + '\'' +
                ", object='" + object + '\'' +
                ", created=" + created +
                ", sentenceId=" + sentenceId +
                ", isEnd=" + isEnd +
                ", isTruncated=" + isTruncated +
                ", result='" + result + '\'' +
                ", needClearHistory=" + needClearHistory +
                ", banRound=" + banRound +
                ", usage=" + usage +
                ", functionCall=" + functionCall +
                ", finishReason=" + finishReason +
                '}';
    }

    public String getId() {
        return id;
    }

    public Integer getErrorCode() {
        return errorCode;
    }

    public String getErrorMsg() {
        return errorMsg;
    }

    public String getObject() {
        return object;
    }

    public Integer getCreated() {
        return created;
    }

    public Integer getSentenceId() {
        return sentenceId;
    }

    public Boolean getIsEnd() {
        return isEnd;
    }

    public Boolean getIsTruncated() {
        return isTruncated;
    }

    public String getResult() {
        return result;
    }

    public Boolean getNeedClearHistory() {
        return needClearHistory;
    }

    public Integer getBanRound() {
        return banRound;
    }

    public Usage getUsage() {
        return usage;
    }

    public FunctionCall getFunctionCall() {
        return functionCall;
    }

    public String getFinishReason() {
        return finishReason;
    }


    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String id;
        private String object;
        private Integer created;
        private Integer sentenceId;
        private Boolean isEnd;
        private Boolean isTruncated;
        private String result;
        private Boolean needClearHistory;
        private Integer banRound;
        private Usage usage;
        private FunctionCall functionCall;
        private  Integer errorCode;
        private  String errorMsg;

        private  String finishReason;

        private Builder() {
        }
        public Builder errorCode(Integer errorCode) {
            this.errorCode = errorCode;
            return this;
        }
        public Builder errorMsg(String errorMsg) {
            this.errorMsg = errorMsg;
            return this;
        }

        public Builder created(Integer created) {
            this.created = created;
            return this;
        }

        public Builder object(String object) {
            this.object = object;
            return this;
        }

        public Builder sentenceId(Integer sentenceId) {
            this.sentenceId = sentenceId;
            return this;
        }

        public Builder isEnd(Boolean isEnd) {
            this.isEnd = isEnd;
            return this;
        }

        public Builder result(String result) {
            this.result = result;
            return this;
        }

        public Builder needClearHistory(Boolean needClearHistory) {
            this.needClearHistory = needClearHistory;
            return this;
        }

        public Builder banRound(Integer banRound) {
            this.banRound = banRound;
            return this;
        }

        public Builder usage(Usage usage) {
            this.usage = usage;
            return this;
        }

        public Builder functionCall(FunctionCall functionCall) {
            this.functionCall = functionCall;
            return this;
        }
        public Builder isTruncated(Boolean isTruncated ) {
            this.isTruncated = isTruncated;
            return this;
        }

        public Builder finishReason(String finishReason ) {
            this.finishReason = finishReason;
            return this;
        }

        public ChatCompletionResponse build() {
            return new ChatCompletionResponse(this);
        }
    }
}

