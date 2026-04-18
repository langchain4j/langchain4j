package dev.langchain4j.model.anthropic.internal.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import dev.langchain4j.model.anthropic.AnthropicThinkingDisplay;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AnthropicThinking {

    /**
     * Thinking type. Accepted values:
     * <ul>
     *   <li>{@code "enabled"}  – manual extended thinking with a fixed token budget (deprecated on Opus 4.6 / Sonnet 4.6).</li>
     *   <li>{@code "adaptive"} – adaptive thinking, recommended for Opus 4.6 and Sonnet 4.6.
     *       Claude decides when and how much to think based on request complexity.
     *       Use together with {@code output_config.effort} instead of {@code budget_tokens}.</li>
     *   <li>{@code "disabled"} – thinking disabled.</li>
     * </ul>
     */
    @JsonProperty
    private final String type;

    /**
     * Maximum thinking tokens for manual ({@code "enabled"}) mode.
     * Deprecated on Opus 4.6 / Sonnet 4.6 – use adaptive mode with {@code effort} instead.
     */
    @JsonProperty
    private final Integer budgetTokens;

    /**
     * Controls how thinking content is returned in API responses.
     * <ul>
     *   <li>{@code "summarized"} (default) – thinking blocks contain summarized text.</li>
     *   <li>{@code "omitted"} – thinking blocks are returned with an empty {@code thinking} field;
     *       the encrypted {@code signature} is still included for multi-turn continuity.
     *       Reduces time-to-first-text-token when streaming.</li>
     * </ul>
     * Invalid when {@code type} is {@code "disabled"}.
     */
    @JsonProperty
    private final AnthropicThinkingDisplay display;

    public AnthropicThinking(Builder builder) {
        this.type = builder.type;
        this.budgetTokens = builder.budgetTokens;
        this.display = builder.display;
    }

    public String getType() {
        return type;
    }

    public Integer getBudgetTokens() {
        return budgetTokens;
    }

    public AnthropicThinkingDisplay getDisplay() {
        return display;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String type;
        private Integer budgetTokens;
        private AnthropicThinkingDisplay display;

        private Builder() {}

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder budgetTokens(Integer budgetTokens) {
            this.budgetTokens = budgetTokens;
            return this;
        }

        /**
         * Controls how thinking content is returned.
         */
        public Builder display(AnthropicThinkingDisplay display) {
            this.display = display;
            return this;
        }

        public AnthropicThinking build() {
            return new AnthropicThinking(this);
        }
    }
}
