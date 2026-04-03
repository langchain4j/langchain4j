package dev.langchain4j.model.vertexai.anthropic.internal.api;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import dev.langchain4j.model.vertexai.anthropic.internal.Constants;
import java.util.List;

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
}
