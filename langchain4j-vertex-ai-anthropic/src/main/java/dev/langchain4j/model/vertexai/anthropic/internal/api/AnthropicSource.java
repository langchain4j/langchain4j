package dev.langchain4j.model.vertexai.anthropic.internal.api;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class AnthropicSource {

    public String type;
    public String mediaType;
    public String data;

    public AnthropicSource() {}

    public AnthropicSource(String type, String mediaType, String data) {
        this.type = type;
        this.mediaType = mediaType;
        this.data = data;
    }

    public static AnthropicSource base64(String mediaType, String data) {
        return new AnthropicSource("base64", mediaType, data);
    }
}
