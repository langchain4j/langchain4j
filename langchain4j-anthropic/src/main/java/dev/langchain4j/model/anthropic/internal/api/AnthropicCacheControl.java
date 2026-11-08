package dev.langchain4j.model.anthropic.internal.api;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class AnthropicCacheControl {

    private final String type;
    private final String ttl;

    public AnthropicCacheControl(String type) {
        this(type, null);
    }

    public AnthropicCacheControl(String type, String ttl) {
        this.type = type;
        this.ttl = ttl;
    }

    public String getType() {
        return type;
    }

    public String getTtl() {
        return ttl;
    }

    @Override
    public String toString() {
        return "AnthropicCacheControl{type='" + type + "', ttl='" + ttl + "'}";
    }
}
