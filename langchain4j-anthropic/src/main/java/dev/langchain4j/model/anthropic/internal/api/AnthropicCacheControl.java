package dev.langchain4j.model.anthropic.internal.api;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class AnthropicCacheControl {

    private final String type;

    public AnthropicCacheControl(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }
}
