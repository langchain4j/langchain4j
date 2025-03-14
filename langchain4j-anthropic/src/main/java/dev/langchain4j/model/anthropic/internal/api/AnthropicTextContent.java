package dev.langchain4j.model.anthropic.internal.api;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.Objects;

@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
public class AnthropicTextContent extends AnthropicMessageContent {

    public String text;

    public AnthropicTextContent(String text) {
        super("text");
        this.text = text;
    }

    public AnthropicTextContent(String text, AnthropicCacheControl cacheControl) {
        super("text", cacheControl);
        this.text = text;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        AnthropicTextContent that = (AnthropicTextContent) o;
        return Objects.equals(text, that.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), text);
    }

    @Override
    public String toString() {
        return "AnthropicTextContent{" + "text='"
                + text + '\'' + ", type='"
                + type + '\'' + ", cacheControl="
                + cacheControl + '}';
    }
}
