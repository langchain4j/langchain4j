package dev.langchain4j.model.zhipu;

import com.google.gson.annotations.SerializedName;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
class ZhipuAiChatCompletionMessage {
    private ZhipuAiChatCompletionRole role;
    private String content;
    private String name;
    @SerializedName("tool_calls")
    private List<ZhipuAiChatCompletionToolCall> toolCalls;
}
