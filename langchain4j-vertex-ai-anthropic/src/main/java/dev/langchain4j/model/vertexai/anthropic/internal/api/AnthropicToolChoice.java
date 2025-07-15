package dev.langchain4j.model.vertexai.anthropic.internal.api;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
public class AnthropicToolChoice {

    public String type;
    public String name;

    public AnthropicToolChoice() {}

    public AnthropicToolChoice(String type, String name) {
        this.type = type;
        this.name = name;
    }

    public static AnthropicToolChoice auto() {
        return new AnthropicToolChoice("auto", null);
    }

    public static AnthropicToolChoice any() {
        return new AnthropicToolChoice("any", null);
    }

    public static AnthropicToolChoice tool(String name) {
        return new AnthropicToolChoice("tool", name);
    }
}
