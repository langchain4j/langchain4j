package dev.langchain4j.model.jinaAi;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;

@Getter
class Usage {
    @SerializedName("total_tokens")
    private Integer totalTokens;
    @SerializedName("prompt_tokens")
    private Integer promptTokens;
}
