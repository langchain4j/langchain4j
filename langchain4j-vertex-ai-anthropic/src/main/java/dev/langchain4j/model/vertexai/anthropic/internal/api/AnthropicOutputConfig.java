package dev.langchain4j.model.vertexai.anthropic.internal.api;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class AnthropicOutputConfig {

    public String effort;

    public AnthropicOutputConfig() {}

    public AnthropicOutputConfig(String effort) {
        this.effort = effort;
    }

    @Override
    public String toString() {
        return "AnthropicOutputConfig [effort=" + effort + "]";
    }
}
