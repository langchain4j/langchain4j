package dev.langchain4j.model.zhipu;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
class ZhipuAiChatCompletionChoice {
    @SerializedName("finish_reason")
    private String finishReason;
    @SerializedName("index")
    private Long index;
    @SerializedName("message")
    private ZhipuAiChatCompletionMessage message;
    @SerializedName("delta")
    private ZhipuAiChatCompletionDelta delta;
}