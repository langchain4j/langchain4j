package dev.langchain4j.model.zhipu.chat;

import com.google.gson.annotations.SerializedName;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
@EqualsAndHashCode
public final class ChatCompletionChoice {

    private final Integer index;
    private final AssistantMessage message;
    private final Delta delta;
    @SerializedName("finish_reason")
    private final String finishReason;

    private ChatCompletionChoice(Builder builder) {
        this.index = builder.index;
        this.message = builder.message;
        this.delta = builder.delta;
        this.finishReason = builder.finishReason;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Integer index;
        private AssistantMessage message;
        private Delta delta;
        private String finishReason;

        private Builder() {
        }

        public Builder index(Integer index) {
            this.index = index;
            return this;
        }

        public Builder message(AssistantMessage message) {
            this.message = message;
            return this;
        }

        public Builder delta(Delta delta) {
            this.delta = delta;
            return this;
        }

        public Builder finishReason(String finishReason) {
            this.finishReason = finishReason;
            return this;
        }

        public ChatCompletionChoice build() {
            return new ChatCompletionChoice(this);
        }
    }
}