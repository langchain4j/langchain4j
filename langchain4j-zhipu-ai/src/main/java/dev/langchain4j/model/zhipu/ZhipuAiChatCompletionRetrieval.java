package dev.langchain4j.model.zhipu;

import com.google.gson.annotations.SerializedName;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;

@Data
@Builder
@ToString
class ZhipuAiChatCompletionRetrieval {
    @SerializedName("knowledge_id")
    private String knowledgeId;
    @SerializedName("prompt_template")
    private String promptTemplate;
}
