package dev.langchain4j.model.anthropic;

import static dev.langchain4j.model.anthropic.AnthropicChatModel.toThinking;
import static dev.langchain4j.model.anthropic.InternalAnthropicHelper.toAnthropicOutputConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.anthropic.internal.api.AnthropicOutputConfig;
import dev.langchain4j.model.anthropic.internal.api.AnthropicThinking;
import dev.langchain4j.model.chat.request.ResponseFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for the thinking configuration types: {@link AnthropicThinkingEffort},
 * {@link AnthropicThinkingDisplay}, {@link AnthropicThinking}, and {@link AnthropicOutputConfig}.
 * <p>
 * Tests cover:
 * <ul>
 *   <li>Enum JSON serialization via {@code @JsonProperty}</li>
 *   <li>{@code toThinking()} factory method in all combinations</li>
 *   <li>{@code toAnthropicOutputConfig()} null-handling and merging</li>
 * </ul>
 */
class AnthropicThinkingConfigTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // -------------------------------------------------------------------------
    // AnthropicThinkingEffort – serialization
    // -------------------------------------------------------------------------

    @Test
    void effort_MAX_serializes_to_max() throws JsonProcessingException {
        assertThat(MAPPER.writeValueAsString(AnthropicThinkingEffort.MAX)).isEqualTo("\"max\"");
    }

    @Test
    void effort_HIGH_serializes_to_high() throws JsonProcessingException {
        assertThat(MAPPER.writeValueAsString(AnthropicThinkingEffort.HIGH)).isEqualTo("\"high\"");
    }

    @Test
    void effort_MEDIUM_serializes_to_medium() throws JsonProcessingException {
        assertThat(MAPPER.writeValueAsString(AnthropicThinkingEffort.MEDIUM)).isEqualTo("\"medium\"");
    }

    @Test
    void effort_LOW_serializes_to_low() throws JsonProcessingException {
        assertThat(MAPPER.writeValueAsString(AnthropicThinkingEffort.LOW)).isEqualTo("\"low\"");
    }

    @Test
    void effort_toString_is_lowercase() {
        assertThat(AnthropicThinkingEffort.MAX.toString()).isEqualTo("max");
        assertThat(AnthropicThinkingEffort.HIGH.toString()).isEqualTo("high");
        assertThat(AnthropicThinkingEffort.MEDIUM.toString()).isEqualTo("medium");
        assertThat(AnthropicThinkingEffort.LOW.toString()).isEqualTo("low");
    }

    // -------------------------------------------------------------------------
    // AnthropicThinkingDisplay – serialization
    // -------------------------------------------------------------------------

    @Test
    void display_SUMMARIZED_serializes_to_summarized() throws JsonProcessingException {
        assertThat(MAPPER.writeValueAsString(AnthropicThinkingDisplay.SUMMARIZED))
                .isEqualTo("\"summarized\"");
    }

    @Test
    void display_OMITTED_serializes_to_omitted() throws JsonProcessingException {
        assertThat(MAPPER.writeValueAsString(AnthropicThinkingDisplay.OMITTED)).isEqualTo("\"omitted\"");
    }

    @Test
    void display_toString_is_lowercase() {
        assertThat(AnthropicThinkingDisplay.SUMMARIZED.toString()).isEqualTo("summarized");
        assertThat(AnthropicThinkingDisplay.OMITTED.toString()).isEqualTo("omitted");
    }

    // -------------------------------------------------------------------------
    // toThinking() – factory method
    // -------------------------------------------------------------------------

    @Test
    void toThinking_returns_null_when_type_and_budget_and_display_are_all_null() {
        assertThat(toThinking(null, null, null)).isNull();
    }

    @Test
    void toThinking_enabled_with_budget_no_display() throws JsonProcessingException {
        AnthropicThinking thinking = toThinking("enabled", 1024, null);

        assertThat(thinking).isNotNull();
        assertThat(thinking.getType()).isEqualTo("enabled");
        assertThat(thinking.getBudgetTokens()).isEqualTo(1024);
        assertThat(thinking.getDisplay()).isNull();

        // display must be absent from JSON (NON_NULL)
        String json = MAPPER.writeValueAsString(thinking);
        assertThat(json).contains("\"type\":\"enabled\"");
        assertThat(json).contains("\"budget_tokens\":1024");
        assertThat(json).doesNotContain("display");
    }

    @Test
    void toThinking_adaptive_no_budget_no_display() throws JsonProcessingException {
        AnthropicThinking thinking = toThinking("adaptive", null, null);

        assertThat(thinking).isNotNull();
        assertThat(thinking.getType()).isEqualTo("adaptive");
        assertThat(thinking.getBudgetTokens()).isNull();
        assertThat(thinking.getDisplay()).isNull();

        String json = MAPPER.writeValueAsString(thinking);
        assertThat(json).contains("\"type\":\"adaptive\"");
        assertThat(json).doesNotContain("budget_tokens");
        assertThat(json).doesNotContain("display");
    }

    @Test
    void toThinking_adaptive_with_display_OMITTED() throws JsonProcessingException {
        AnthropicThinking thinking = toThinking("adaptive", null, AnthropicThinkingDisplay.OMITTED);

        assertThat(thinking.getType()).isEqualTo("adaptive");
        assertThat(thinking.getDisplay()).isEqualTo(AnthropicThinkingDisplay.OMITTED);

        String json = MAPPER.writeValueAsString(thinking);
        assertThat(json).contains("\"type\":\"adaptive\"");
        assertThat(json).contains("\"display\":\"omitted\"");
        assertThat(json).doesNotContain("budget_tokens");
    }

    @Test
    void toThinking_enabled_with_display_SUMMARIZED() throws JsonProcessingException {
        AnthropicThinking thinking = toThinking("enabled", 2048, AnthropicThinkingDisplay.SUMMARIZED);

        assertThat(thinking.getBudgetTokens()).isEqualTo(2048);
        assertThat(thinking.getDisplay()).isEqualTo(AnthropicThinkingDisplay.SUMMARIZED);

        String json = MAPPER.writeValueAsString(thinking);
        assertThat(json).contains("\"type\":\"enabled\"");
        assertThat(json).contains("\"budget_tokens\":2048");
        assertThat(json).contains("\"display\":\"summarized\"");
    }

    @Test
    void toThinking_returns_non_null_when_only_budget_is_set() {
        // budgetTokens alone (without type) should still produce an object
        AnthropicThinking thinking = toThinking(null, 512, null);
        assertThat(thinking).isNotNull();
        assertThat(thinking.getBudgetTokens()).isEqualTo(512);
        assertThat(thinking.getType()).isNull();
    }

    // -------------------------------------------------------------------------
    // toAnthropicOutputConfig() – null-handling and effort/format merging
    // -------------------------------------------------------------------------

    @Test
    void toAnthropicOutputConfig_returns_null_when_both_args_null() {
        assertThat(toAnthropicOutputConfig(null, null)).isNull();
        assertThat(toAnthropicOutputConfig(null)).isNull();
    }

    @Test
    void toAnthropicOutputConfig_returns_null_for_TEXT_response_format_and_null_effort() {
        assertThat(toAnthropicOutputConfig(ResponseFormat.TEXT, null)).isNull();
    }

    @Test
    void toAnthropicOutputConfig_with_effort_only_omits_format_field() throws JsonProcessingException {
        AnthropicOutputConfig config = toAnthropicOutputConfig(null, AnthropicThinkingEffort.MEDIUM);

        assertThat(config).isNotNull();
        assertThat(config.getEffort()).isEqualTo(AnthropicThinkingEffort.MEDIUM);
        assertThat(config.getFormat()).isNull();

        String json = MAPPER.writeValueAsString(config);
        assertThat(json).contains("\"effort\":\"medium\"");
        assertThat(json).doesNotContain("format");
    }

    @Test
    void toAnthropicOutputConfig_with_TEXT_format_and_effort_includes_only_effort() throws JsonProcessingException {
        // TEXT response format with no JSON schema → format stays null, effort is set
        AnthropicOutputConfig config = toAnthropicOutputConfig(ResponseFormat.TEXT, AnthropicThinkingEffort.LOW);

        assertThat(config).isNotNull();
        assertThat(config.getEffort()).isEqualTo(AnthropicThinkingEffort.LOW);
        assertThat(config.getFormat()).isNull();

        String json = MAPPER.writeValueAsString(config);
        assertThat(json).contains("\"effort\":\"low\"");
        assertThat(json).doesNotContain("format");
    }

    @ParameterizedTest
    @CsvSource({"MAX,max", "HIGH,high", "MEDIUM,medium", "LOW,low"})
    void toAnthropicOutputConfig_effort_serializes_correctly(AnthropicThinkingEffort effort, String expectedJson)
            throws JsonProcessingException {
        AnthropicOutputConfig config = toAnthropicOutputConfig(null, effort);
        assertThat(MAPPER.writeValueAsString(config)).contains("\"effort\":\"" + expectedJson + "\"");
    }

    // -------------------------------------------------------------------------
    // AnthropicOutputConfig – equals / hashCode
    // -------------------------------------------------------------------------

    @Test
    void outputConfig_equals_same_effort() {
        AnthropicOutputConfig a = AnthropicOutputConfig.builder()
                .effort(AnthropicThinkingEffort.HIGH)
                .build();
        AnthropicOutputConfig b = AnthropicOutputConfig.builder()
                .effort(AnthropicThinkingEffort.HIGH)
                .build();
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void outputConfig_not_equals_different_effort() {
        AnthropicOutputConfig a = AnthropicOutputConfig.builder()
                .effort(AnthropicThinkingEffort.HIGH)
                .build();
        AnthropicOutputConfig b = AnthropicOutputConfig.builder()
                .effort(AnthropicThinkingEffort.LOW)
                .build();
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void outputConfig_toString_includes_effort() {
        AnthropicOutputConfig config = AnthropicOutputConfig.builder()
                .effort(AnthropicThinkingEffort.MEDIUM)
                .build();
        assertThat(config.toString()).contains("medium");
    }

    // -------------------------------------------------------------------------
    // AnthropicThinking – builder edge cases
    // -------------------------------------------------------------------------

    @Test
    void thinking_builder_produces_correct_object() {
        AnthropicThinking thinking = AnthropicThinking.builder()
                .type("adaptive")
                .display(AnthropicThinkingDisplay.OMITTED)
                .build();

        assertThat(thinking.getType()).isEqualTo("adaptive");
        assertThat(thinking.getBudgetTokens()).isNull();
        assertThat(thinking.getDisplay()).isEqualTo(AnthropicThinkingDisplay.OMITTED);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(booleans = false)
    void toThinking_returns_null_when_type_null_and_budget_null(Boolean ignored) {
        // display alone without type/budget is meaningless — toThinking must return null
        assertThat(toThinking(null, null, AnthropicThinkingDisplay.SUMMARIZED)).isNull();
    }
}
