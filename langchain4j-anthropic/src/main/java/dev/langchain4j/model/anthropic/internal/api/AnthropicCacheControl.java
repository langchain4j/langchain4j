package dev.langchain4j.model.anthropic.internal.api;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
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
}
