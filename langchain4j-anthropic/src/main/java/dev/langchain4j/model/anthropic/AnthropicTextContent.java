package dev.langchain4j.model.anthropic;

import lombok.EqualsAndHashCode;
import lombok.ToString;

@ToString
@EqualsAndHashCode(callSuper = true)
public class AnthropicTextContent extends AnthropicMessageContent {

    public String text;

    public AnthropicTextContent(String text) {
        super("text");
        this.text = text;
    }
}
