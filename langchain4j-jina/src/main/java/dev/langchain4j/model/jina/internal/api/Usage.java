package dev.langchain4j.model.jina.internal.api;

import com.google.gson.annotations.SerializedName;

public class Usage {

    @SerializedName("total_tokens")
    public Integer totalTokens;

    @SerializedName("prompt_tokens")
    public Integer promptTokens;
}
