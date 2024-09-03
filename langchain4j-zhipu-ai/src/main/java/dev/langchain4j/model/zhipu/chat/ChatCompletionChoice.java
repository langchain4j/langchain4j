package dev.langchain4j.model.zhipu.chat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonInclude(NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public final class ChatCompletionChoice {

    private Integer index;
    private AssistantMessage message;
    private Delta delta;
    private String finishReason;

    public ChatCompletionChoice(Integer index, AssistantMessage message, Delta delta, String finishReason) {
        this.index = index;
        this.message = message;
        this.delta = delta;
        this.finishReason = finishReason;
    }

    public static ChatCompletionChoiceBuilder builder() {
        return new ChatCompletionChoiceBuilder();
    }

    public Integer getIndex() {
        return index;
    }

    public void setIndex(Integer index) {
        this.index = index;
    }

    public AssistantMessage getMessage() {
        return message;
    }

    public void setMessage(AssistantMessage message) {
        this.message = message;
    }

    public Delta getDelta() {
        return delta;
    }

    public void setDelta(Delta delta) {
        this.delta = delta;
    }

    public String getFinishReason() {
        return finishReason;
    }

    public void setFinishReason(String finishReason) {
        this.finishReason = finishReason;
    }

    public static class ChatCompletionChoiceBuilder {
        private Integer index;
        private AssistantMessage message;
        private Delta delta;
        private String finishReason;

        public ChatCompletionChoiceBuilder index(Integer index) {
            this.index = index;
            return this;
        }

        public ChatCompletionChoiceBuilder message(AssistantMessage message) {
            this.message = message;
            return this;
        }

        public ChatCompletionChoiceBuilder delta(Delta delta) {
            this.delta = delta;
            return this;
        }

        public ChatCompletionChoiceBuilder finishReason(String finishReason) {
            this.finishReason = finishReason;
            return this;
        }

        public ChatCompletionChoice build() {
            return new ChatCompletionChoice(this.index, this.message, this.delta, this.finishReason);
        }

        public String toString() {
            return "ChatCompletionChoice.ChatCompletionChoiceBuilder(index=" + this.index + ", message=" + this.message + ", delta=" + this.delta + ", finishReason=" + this.finishReason + ")";
        }
    }
}