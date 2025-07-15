package dev.langchain4j.model.vertexai.anthropic.internal.api;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
public class AnthropicContent {

    public String type;
    public String text;
    public AnthropicSource source;
    public String id;
    public String name;
    public Object input;
    public AnthropicCacheControl cacheControl;

    public AnthropicContent() {}

    public AnthropicContent(String type, String text) {
        this.type = type;
        this.text = text;
    }

    public static AnthropicContent textContent(String text) {
        return new AnthropicContent("text", text);
    }

    public static AnthropicContent textContentWithCache(String text, AnthropicCacheControl cacheControl) {
        AnthropicContent content = new AnthropicContent("text", text);
        content.cacheControl = cacheControl;
        return content;
    }

    public static AnthropicContent imageContent(AnthropicSource source) {
        AnthropicContent content = new AnthropicContent();
        content.type = "image";
        content.source = source;
        return content;
    }

    public static AnthropicContent toolUse(String id, String name, Object input) {
        AnthropicContent content = new AnthropicContent();
        content.type = "tool_use";
        content.id = id;
        content.name = name;
        content.input = input;
        return content;
    }

    public static AnthropicContent toolResult(String toolUseId, String text) {
        AnthropicContent content = new AnthropicContent();
        content.type = "tool_result";
        content.id = toolUseId;
        content.text = text;
        return content;
    }
}
