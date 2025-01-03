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
public class MistralAiChatCompletionChoice {
    private Integer index;
    private MistralAiChatMessage message;
    private MistralAiDeltaMessage delta;
    private String finishReason;
    private MistralAiUsage usage; // usageInfo is returned only when the prompt is finished in stream mode

    public MistralAiChatCompletionChoice() {}

    public Integer getIndex() {
        return this.index;
    }

    public MistralAiChatMessage getMessage() {
        return this.message;
    }

    public MistralAiDeltaMessage getDelta() {
        return this.delta;
    }

    public String getFinishReason() {
        return this.finishReason;
    }

    public MistralAiUsage getUsage() {
        return this.usage;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        final MistralAiChatCompletionChoice other = (MistralAiChatCompletionChoice) obj;
        return Objects.equals(this.finishReason, other.finishReason)
                && Objects.equals(this.index, other.index)
                && Objects.equals(this.message, other.message)
                && Objects.equals(this.delta, other.delta)
                && Objects.equals(this.usage, other.usage);
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 59 * hash + Objects.hashCode(this.index);
        hash = 59 * hash + Objects.hashCode(this.message);
        hash = 59 * hash + Objects.hashCode(this.delta);
        hash = 59 * hash + Objects.hashCode(this.finishReason);
        hash = 59 * hash + Objects.hashCode(this.usage);
        return hash;
    }

    @Override
    public String toString() {
        return "MistralAiChatCompletionChoice(" + "index=" + this.getIndex() + ", message=" + this.getMessage() == null
                ? ""
                : "**********"
                        + ", delta=" + this.getDelta()
                        + ", finishReason=" + this.getFinishReason()
                        + ", usage=" + this.getUsage() + ")";
    }
}
