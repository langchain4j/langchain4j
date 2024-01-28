package dev.langchain4j.model.zhipu;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
enum ZhipuAiChatCompletionRole {
    @SerializedName("system") SYSTEM,
    @SerializedName("user") USER,
    @SerializedName("assistant") ASSISTANT,
    @SerializedName("function") FUNCTION,
    @SerializedName("tool") TOOL,
    ;
}