package dev.langchain4j.model.mistralai.internal.api;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.Objects;

@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
public class MistralAiUsage {
    private Integer promptTokens;
    private Integer totalTokens;
    private Integer completionTokens;

    public MistralAiUsage() {}

    public Integer getPromptTokens() {
        return this.promptTokens;
    }

    public Integer getTotalTokens() {
        return this.totalTokens;
    }

    public Integer getCompletionTokens() {
        return this.completionTokens;
    }

    public void setPromptTokens(Integer promptTokens) {
        this.promptTokens = promptTokens;
    }

    public void setTotalTokens(Integer totalTokens) {
        this.totalTokens = totalTokens;
    }

    public void setCompletionTokens(Integer completionTokens) {
        this.completionTokens = completionTokens;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 89 * hash + Objects.hashCode(this.promptTokens);
        hash = 89 * hash + Objects.hashCode(this.totalTokens);
        hash = 89 * hash + Objects.hashCode(this.completionTokens);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        final MistralAiUsage other = (MistralAiUsage) obj;
        return Objects.equals(this.promptTokens, other.promptTokens)
                && Objects.equals(this.totalTokens, other.totalTokens)
                && Objects.equals(this.completionTokens, other.completionTokens);
    }

    @Override
    public String toString() {
        return "MistralAiUsage("
                + "promptTokens=" + this.getPromptTokens()
                + ", totalTokens=" + this.getTotalTokens()
                + ", completionTokens=" + this.getCompletionTokens()
                + ")";
    }
}
