package dev.langchain4j.model.anthropic;

import static dev.langchain4j.model.anthropic.AnthropicChatModel.toThinking;
import static dev.langchain4j.model.anthropic.InternalAnthropicHelper.toAnthropicOutputConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.anthropic.internal.api.AnthropicOutputConfig;
import dev.langchain4j.model.anthropic.internal.api.AnthropicThinking;
import dev.langchain4j.model.chat.request.ResponseFormat;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

class AnthropicThinkingConfigTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // -------------------------------------------------------------------------
    // Enum serialization + toString
    // -------------------------------------------------------------------------

    @ParameterizedTest
    @CsvSource({"MAX,max", "HIGH,high", "MEDIUM,medium", "LOW,low"})
    void effort_enum_should_serialize_and_to_string_correctly(AnthropicThinkingEffort effort, String expected)
            throws JsonProcessingException {

        assertThat(MAPPER.writeValueAsString(effort)).isEqualTo("\"" + expected + "\"");

        assertThat(effort.toString()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"SUMMARIZED,summarized", "OMITTED,omitted"})
    void display_enum_should_serialize_and_to_string_correctly(AnthropicThinkingDisplay display, String expected)
            throws JsonProcessingException {

        assertThat(MAPPER.writeValueAsString(display)).isEqualTo("\"" + expected + "\"");

        assertThat(display.toString()).isEqualTo(expected);
    }

    // -------------------------------------------------------------------------
    // toThinking()
    // -------------------------------------------------------------------------

    @ParameterizedTest
    @CsvSource({
        "enabled,1024,,enabled,1024",
        "adaptive,,,adaptive,",
        "adaptive,,OMITTED,adaptive,",
        "enabled,2048,SUMMARIZED,enabled,2048",
        ",512,,,512"
    })
    void to_thinking_should_build_expected_object(
            String type,
            Integer budget,
            AnthropicThinkingDisplay display,
            String expectedType,
            Integer expectedBudget) {
        AnthropicThinking thinking = toThinking(type, budget, display);

        assertThat(thinking).isNotNull();
        assertThat(thinking.getType()).isEqualTo(expectedType);
        assertThat(thinking.getBudgetTokens()).isEqualTo(expectedBudget);
        assertThat(thinking.getDisplay()).isEqualTo(display);
    }

    @ParameterizedTest
    @MethodSource("nullThinkingCases")
    void to_thinking_should_return_null(String type, Integer budget, AnthropicThinkingDisplay display) {
        assertThat(toThinking(type, budget, display)).isNull();
    }

    private static Stream<Arguments> nullThinkingCases() {
        return Stream.of(arguments(null, null, null), arguments(null, null, AnthropicThinkingDisplay.SUMMARIZED));
    }

    // -------------------------------------------------------------------------
    // toAnthropicOutputConfig()
    // -------------------------------------------------------------------------

    @ParameterizedTest
    @MethodSource("nullOutputConfigCases")
    void output_config_should_return_null(ResponseFormat format, AnthropicThinkingEffort effort) {
        assertThat(toAnthropicOutputConfig(format, effort)).isNull();
    }

    private static Stream<Arguments> nullOutputConfigCases() {
        return Stream.of(arguments(null, null), arguments(ResponseFormat.TEXT, null));
    }

    @ParameterizedTest
    @CsvSource({"MAX,max", "HIGH,high", "MEDIUM,medium", "LOW,low"})
    void output_config_should_serialize_effort_correctly(AnthropicThinkingEffort effort, String expected)
            throws JsonProcessingException {

        AnthropicOutputConfig config = toAnthropicOutputConfig(null, effort);

        assertThat(config).isNotNull();
        assertThat(config.getEffort()).isEqualTo(effort);
        assertThat(config.getFormat()).isNull();

        assertThat(MAPPER.writeValueAsString(config))
                .contains("\"effort\":\"" + expected + "\"")
                .doesNotContain("format");
    }
}
