package dev.langchain4j.model.zhipu;

import com.google.gson.annotations.SerializedName;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;

import java.util.List;

@Data
@Builder
@ToString
public final class ZhipuAiChatCompletionRequest {
    private ZhipuAiChatModelEnum model;
    private List<ZhipuAiChatCompletionMessage> messages;
    @SerializedName("request_id")
    private String requestId;
    @SerializedName("do_sample")
    private String doSample;
    private Boolean stream;
    private Double temperature;
    @SerializedName("top_p")
    private Double topP;
    @SerializedName("max_tokens")
    private Integer maxTokens;
    private List<String> stop;
    private List<ZhipuAiChatCompletionTool> tools;
    @SerializedName("tool_choice")
    private String toolChoice;
}
