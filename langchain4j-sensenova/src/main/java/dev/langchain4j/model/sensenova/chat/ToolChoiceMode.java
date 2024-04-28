package dev.langchain4j.model.sensenova.chat;

import com.google.gson.annotations.SerializedName;

public enum ToolChoiceMode {
    @SerializedName("none") NONE,
    @SerializedName("auto") AUTO
}