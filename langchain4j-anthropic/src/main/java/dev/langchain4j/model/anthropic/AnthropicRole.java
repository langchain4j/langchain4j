package dev.langchain4j.model.anthropic;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;

@Getter
enum AnthropicRole {
    @SerializedName("user") USER,
    @SerializedName("assistant") ASSISTANT;

    AnthropicRole() {
    }
}
