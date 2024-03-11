package dev.langchain4j.model.zhipu.chat;

import com.google.gson.annotations.SerializedName;

public enum Role {
    @SerializedName("system") SYSTEM,
    @SerializedName("user") USER,
    @SerializedName("assistant") ASSISTANT,
    @SerializedName("function") FUNCTION,
    @SerializedName("tool") TOOL
}