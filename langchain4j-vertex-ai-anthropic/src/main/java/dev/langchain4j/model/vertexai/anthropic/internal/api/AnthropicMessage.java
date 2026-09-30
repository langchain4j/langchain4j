package dev.langchain4j.model.vertexai.anthropic.internal.api;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class AnthropicMessage {

    public String role;
    public List<AnthropicContent> content;
    public AnthropicCacheControl cacheControl;

    public AnthropicMessage() {}

    public AnthropicMessage(String role, List<AnthropicContent> content) {
        this.role = role;
        this.content = content;
    }
}
