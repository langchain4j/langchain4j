package dev.langchain4j.model.zhipu;

import com.google.gson.annotations.SerializedName;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;

@Data
@Builder
@ToString
class ZhipuAiChatCompletionTool {
    private String type;
    private ZhipuAiChatCompletionFunction function;
    private ZhipuAiChatCompletionRetrieval retrieval;
    @SerializedName("web_search")
    private ZhipuAiChatCompletionWebSearch webSearch;
}
