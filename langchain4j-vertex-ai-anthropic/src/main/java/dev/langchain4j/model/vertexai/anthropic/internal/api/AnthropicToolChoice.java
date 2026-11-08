package dev.langchain4j.model.vertexai.anthropic.internal.api;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.StringJoiner;

@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class AnthropicToolChoice {

    public String type;
    public String name;

    public AnthropicToolChoice() {}

    public AnthropicToolChoice(String type, String name) {
        this.type = type;
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", "AnthropicToolChoice [", "]")
                .add("name=" + this.getName())
                .add("type=" + this.getType())
                .toString();
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
