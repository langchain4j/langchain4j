package dev.langchain4j.model.anthropic.internal.api;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.Objects;

/**
 * The reason a cache-diagnostics comparison found a divergence, or why no comparison was produced.
 * <p>
 * {@code type} is a discriminator (e.g. {@code "model_changed"}, {@code "system_changed"},
 * {@code "tools_changed"}, {@code "messages_changed"}, {@code "previous_message_not_found"},
 * {@code "unavailable"}). {@code cacheMissedInputTokens} is only present for the {@code "*_changed"} types.
 * <p>
 * Kept as a flat, loosely-typed POJO (rather than a discriminated union of subclasses) since this
 * is a beta feature whose field names and semantics may change before general availability.
 */
@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
public class AnthropicCacheMissReason {

    public String type;
    public Integer cacheMissedInputTokens;

    public AnthropicCacheMissReason() {}

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Integer getCacheMissedInputTokens() {
        return cacheMissedInputTokens;
    }

    public void setCacheMissedInputTokens(Integer cacheMissedInputTokens) {
        this.cacheMissedInputTokens = cacheMissedInputTokens;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AnthropicCacheMissReason)) return false;
        AnthropicCacheMissReason that = (AnthropicCacheMissReason) o;
        return Objects.equals(type, that.type) && Objects.equals(cacheMissedInputTokens, that.cacheMissedInputTokens);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, cacheMissedInputTokens);
    }

    @Override
    public String toString() {
        return "AnthropicCacheMissReason{" + "type='"
                + type + '\'' + ", cacheMissedInputTokens="
                + cacheMissedInputTokens + '}';
    }
}
