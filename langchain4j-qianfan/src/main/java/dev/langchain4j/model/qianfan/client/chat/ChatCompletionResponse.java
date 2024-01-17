package dev.langchain4j.model.qianfan.client.chat;

import dev.langchain4j.model.qianfan.client.Usage;

public final class ChatCompletionResponse {

    private final String id;
    private final Integer errorCode;
    private final String errorMsg;
    private final String object;
    private final Integer created;
    private final Integer sentence_id;
    private final Boolean is_end;
    private final Boolean is_truncated;
    private final String result;
    private final Boolean need_clear_history;
    private final Integer ban_round;
    private final Usage usage;
    private final FunctionCall function_call;


    private final String finish_reason;

    private ChatCompletionResponse(Builder builder) {
        this.id = builder.id;
        this.created = builder.created;
        this.object = builder.object;
        this.sentence_id = builder.sentence_id;
        this.is_end = builder.is_end;
        this.is_truncated = builder.is_truncated;
        this.result = builder.result;
        this.need_clear_history = builder.need_clear_history;
        this.ban_round = builder.ban_round;
        this.function_call = builder.function_call;
        this.usage = builder.usage;
        this.errorCode = builder.errorCode;
        this.errorMsg = builder.errorMsg;
        this.finish_reason = builder.finish_reason;
    }



    @Override
    public String toString() {
        return "ChatCompletionResponse{" +
                "id='" + id + '\'' +
                ", errorCode=" + errorCode +
                ", errorMsg='" + errorMsg + '\'' +
                ", object='" + object + '\'' +
                ", created=" + created +
                ", sentence_id=" + sentence_id +
                ", is_end=" + is_end +
                ", is_truncated=" + is_truncated +
                ", result='" + result + '\'' +
                ", need_clear_history=" + need_clear_history +
                ", ban_round=" + ban_round +
                ", usage=" + usage +
                ", function_call=" + function_call +
                ", finish_reason=" + finish_reason +
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

    public Integer getSentence_id() {
        return sentence_id;
    }

    public Boolean getIs_end() {
        return is_end;
    }

    public Boolean getIs_truncated() {
        return is_truncated;
    }

    public String getResult() {
        return result;
    }

    public Boolean getNeed_clear_history() {
        return need_clear_history;
    }

    public Integer getBan_round() {
        return ban_round;
    }

    public Usage getUsage() {
        return usage;
    }

    public FunctionCall getFunction_call() {
        return function_call;
    }

    public String getFinish_reason() {
        return finish_reason;
    }


    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String id;
        private String object;
        private Integer created;
        private Integer sentence_id;
        private Boolean is_end;
        private Boolean is_truncated;
        private String result;
        private Boolean need_clear_history;
        private Integer ban_round;
        private Usage usage;
        private FunctionCall function_call;
        private  Integer errorCode;
        private  String errorMsg;

        private  String finish_reason;

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

        public Builder sentence_id(Integer sentence_id) {
            this.sentence_id = sentence_id;
            return this;
        }

        public Builder is_end(Boolean is_end) {
            this.is_end = is_end;
            return this;
        }

        public Builder result(String result) {
            this.result = result;
            return this;
        }

        public Builder need_clear_history(Boolean need_clear_history) {
            this.need_clear_history = need_clear_history;
            return this;
        }

        public Builder ban_round(Integer ban_round) {
            this.ban_round = ban_round;
            return this;
        }

        public Builder usage(Usage usage) {
            this.usage = usage;
            return this;
        }

        public Builder function_call(FunctionCall function_call) {
            this.function_call = function_call;
            return this;
        }
        public Builder is_truncated(Boolean is_truncated ) {
            this.is_truncated = is_truncated;
            return this;
        }

        public Builder finish_reson(String finish_reason ) {
            this.finish_reason = finish_reason;
            return this;
        }

        public ChatCompletionResponse build() {
            return new ChatCompletionResponse(this);
        }
    }
}

