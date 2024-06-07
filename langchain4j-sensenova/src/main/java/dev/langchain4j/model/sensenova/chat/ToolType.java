package dev.langchain4j.model.sensenova.chat;

import com.google.gson.annotations.SerializedName;

public enum ToolType {
    @SerializedName("function") FUNCTION,
    @SerializedName("web_search") WEB_SEARCH,
    @SerializedName("associated_knowledge") ASSOCIATED_KNOWLEDGE,
    @SerializedName("text2image") TEXT_2_IMAGE,
    @SerializedName("data_analysis") DATA_ANALYSIS,
    @SerializedName("vqa_agent") VQA_AGENT,
    ;
}