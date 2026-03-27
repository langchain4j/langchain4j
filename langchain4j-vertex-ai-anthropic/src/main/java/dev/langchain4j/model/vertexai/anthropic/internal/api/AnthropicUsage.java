package dev.langchain4j.model.vertexai.anthropic.internal.api;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.StringJoiner;

@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
public class AnthropicUsage {

    public Integer inputTokens;
    public Integer outputTokens;
    public Integer cacheCreationInputTokens;
    public Integer cacheReadInputTokens;

    public AnthropicUsage() {}

    public AnthropicUsage(Integer inputTokens, Integer outputTokens) {
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
    }

    public Integer getInputTokens() {
        return inputTokens;
    }

    public Integer getOutputTokens() {
        return outputTokens;
    }

    public Integer getCacheCreationInputTokens() {
        return cacheCreationInputTokens;
    }

    public Integer getCacheReadInputTokens() {
        return cacheReadInputTokens;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", "AnthropicUsage [", "]")
                .add("inputTokens" + this.getInputTokens())
                .add("outputTokens" + this.getOutputTokens())
                .add("cacheCreationInputTokens" + this.getCacheCreationInputTokens())
                .add("cacheReadInputTokens" + this.getCacheReadInputTokens())
                .toString();
    }
}
