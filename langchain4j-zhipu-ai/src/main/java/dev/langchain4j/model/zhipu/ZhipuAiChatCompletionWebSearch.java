package dev.langchain4j.model.zhipu;

import com.google.gson.annotations.SerializedName;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;

@Data
@Builder
@ToString
class ZhipuAiChatCompletionWebSearch {
    private Boolean enable;
    @SerializedName("search_query")
    private String searchQuery;
}