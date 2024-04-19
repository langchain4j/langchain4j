package dev.langchain4j.model.anthropic;

import lombok.EqualsAndHashCode;
import lombok.ToString;

@ToString
@EqualsAndHashCode(callSuper = true)
public class AnthropicImageContent extends AnthropicMessageContent {

    public AnthropicImageContentSource source;

    public AnthropicImageContent(String mediaType, String data) {
        super("image");
        this.source = new AnthropicImageContentSource("base64", mediaType, data);
    }
}
