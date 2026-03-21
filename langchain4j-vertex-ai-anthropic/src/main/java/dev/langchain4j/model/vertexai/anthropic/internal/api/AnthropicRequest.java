package dev.langchain4j.model.vertexai.anthropic.internal.api;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import dev.langchain4j.model.vertexai.anthropic.internal.Constants;
import java.util.List;
import java.util.StringJoiner;

@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
public class AnthropicRequest {

    public String anthropicVersion;
    public List<AnthropicMessage> messages;
    public List<AnthropicSystemMessage> system;
    public Integer maxTokens;
    public List<String> stopSequences;
    public Boolean stream;
    public Double temperature;
    public Double topP;
    public Integer topK;
    public List<AnthropicTool> tools;
    public AnthropicToolChoice toolChoice;

    public AnthropicRequest() {}

    public AnthropicRequest(
            List<AnthropicMessage> messages,
            List<AnthropicSystemMessage> system,
            Integer maxTokens,
            List<String> stopSequences,
            Boolean stream,
            Double temperature,
            Double topP,
            Integer topK,
            List<AnthropicTool> tools,
            AnthropicToolChoice toolChoice) {
        this.messages = messages;
        this.system = system;
        this.maxTokens = maxTokens;
        this.stopSequences = stopSequences;
        this.stream = stream;
        this.temperature = temperature;
        this.topP = topP;
        this.topK = topK;
        this.tools = tools;
        this.toolChoice = toolChoice;
        this.anthropicVersion = Constants.ANTHROPIC_VERSION;
    }

    public String getAnthropicVersion() {
        return anthropicVersion;
    }

    public List<AnthropicMessage> getMessages() {
        return messages;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public Boolean getStream() {
        return stream;
    }

    public Double getTemperature() {
        return temperature;
    }

    public Double getTopP() {
        return topP;
    }

    public Integer getTopK() {
        return topK;
    }

    public List<AnthropicTool> getTools() {
        return tools;
    }

    public AnthropicToolChoice getToolChoice() {
        return toolChoice;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", "AnthropicRequest [", "]")
                .add("anthropicVersion=" + this.getAnthropicVersion())
                .add("messages="
                        + (this.getMessages() == null ? 0 : this.getMessages().size()))
                .add("maxTokens=" + this.getMaxTokens())
                .add("stream=" + this.getStream())
                .add("temperature=" + this.getTemperature())
                .add("topP=" + this.getTopP())
                .add("topK=" + this.getTopK())
                .add("tools=" + this.getTools())
                .add("toolsChoice=" + this.getToolChoice())
                .toString();
    }
}
