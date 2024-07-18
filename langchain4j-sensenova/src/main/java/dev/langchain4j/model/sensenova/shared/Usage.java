package dev.langchain4j.model.sensenova.shared;

import com.google.gson.annotations.SerializedName;
import lombok.*;

@Data
@ToString
@EqualsAndHashCode
@Builder
public final class Usage {
    @SerializedName("prompt_tokens")
    private int promptTokens;

    @SerializedName("completion_tokens")
    private int completionTokens;

    @SerializedName("knowledge_tokens")
    private int knowledgeTokens;

    @SerializedName("total_tokens")
    private int totalTokens;


    public void add(Usage usage) {
        this.promptTokens += usage.promptTokens;
        this.completionTokens += usage.completionTokens;
        this.knowledgeTokens += usage.knowledgeTokens;
        this.totalTokens += usage.totalTokens;
    }

}
