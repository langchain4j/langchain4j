package dev.langchain4j.model.anthropic.internal.api;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * @since 1.2.0
 */
@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
public class AnthropicThinkingContent extends AnthropicMessageContent {

    public String thinking;
    public String signature;

    public AnthropicThinkingContent(String thinking, String signature) {
        super("thinking");
        this.thinking = thinking;
        this.signature = signature;
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        if (!super.equals(object)) return false;
        AnthropicThinkingContent that = (AnthropicThinkingContent) object;
        return Objects.equals(thinking, that.thinking)
                && Objects.equals(signature, that.signature);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), thinking, signature);
    }

    @Override
    public String toString() {
        return "AnthropicThinkingContent{" +
                "thinking='" + thinking + '\'' +
                ", signature='" + signature + '\'' +
                '}';
    }
}
