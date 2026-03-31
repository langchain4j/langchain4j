package dev.langchain4j.model.anthropic;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Controls how thinking content is returned in API responses when extended or adaptive thinking is enabled.
 *
 * @see <a href="https://platform.claude.com/docs/en/build-with-claude/extended-thinking#controlling-thinking-display">Controlling thinking display</a>
 */
public enum AnthropicThinkingDisplay {

    /**
     * Thinking blocks contain summarized thinking text.
     * This is the default when no display value is specified.
     */
    @JsonProperty("summarized")
    SUMMARIZED,

    /**
     * Thinking blocks are returned with an empty {@code thinking} field.
     * The encrypted {@code signature} is still included for multi-turn continuity.
     * <p>
     * Primary benefit: faster time-to-first-text-token when streaming, because the server
     * skips streaming thinking tokens entirely and delivers only the signature.
     * <p>
     * Note: you are still billed for the full thinking tokens; omitting reduces latency, not cost.
     * Cannot be used when thinking type is {@code "disabled"}.
     */
    @JsonProperty("omitted")
    OMITTED;

    @Override
    public String toString() {
        return name().toLowerCase();
    }
}
