package dev.langchain4j.model.anthropic.internal.api;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Objects;

@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class AnthropicMessageContent {

    public String type;
    public AnthropicCacheControl cacheControl;

    public AnthropicMessageContent(String type) {
        this.type = type;
    }

    public AnthropicMessageContent(String type, AnthropicCacheControl cacheControl) {
        this.type = type;
        this.cacheControl = cacheControl;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        AnthropicMessageContent that = (AnthropicMessageContent) o;
        return Objects.equals(type, that.type) && Objects.equals(cacheControl, that.cacheControl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, cacheControl);
    }
}
