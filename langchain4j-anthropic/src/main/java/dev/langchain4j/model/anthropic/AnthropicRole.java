package dev.langchain4j.model.anthropic;

import com.google.gson.annotations.SerializedName;

enum AnthropicRole {

    @SerializedName("user") USER,
    @SerializedName("assistant") ASSISTANT
}
