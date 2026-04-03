package dev.langchain4j.model.vertexai.anthropic.internal.api;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
public class AnthropicSystemMessage {

    public String type;
    public String text;
    public AnthropicCacheControl cacheControl;

    public AnthropicSystemMessage() {}

    public AnthropicSystemMessage(String type, String text) {
        this.type = type;
        this.text = text;
    }

    public static AnthropicSystemMessage textSystemMessage(String text) {
        return new AnthropicSystemMessage("text", text);
    }

    public static AnthropicSystemMessage textSystemMessageWithCache(String text, AnthropicCacheControl cacheControl) {
        AnthropicSystemMessage message = new AnthropicSystemMessage("text", text);
        message.cacheControl = cacheControl;
        return message;
    }
}
