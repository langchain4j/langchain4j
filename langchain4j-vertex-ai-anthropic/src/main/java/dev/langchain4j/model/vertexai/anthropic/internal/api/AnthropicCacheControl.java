package dev.langchain4j.model.vertexai.anthropic.internal.api;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class AnthropicCacheControl {

    public String type;

    public AnthropicCacheControl() {}

    public AnthropicCacheControl(String type) {
        this.type = type;
    }

    public static AnthropicCacheControl ephemeral() {
        return new AnthropicCacheControl("ephemeral");
    }
}
