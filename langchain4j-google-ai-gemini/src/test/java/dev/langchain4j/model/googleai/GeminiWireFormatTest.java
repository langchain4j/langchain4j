package dev.langchain4j.model.googleai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Gemini's JSON API is camelCase, which is what the codec writes. The one field that goes out
 * snake_case says so with {@code @JsonProperty}, rather than a naming strategy that a different
 * JSON library would not see.
 */
class GeminiWireFormatTest {

    @Test
    void should_write_the_allowed_function_names_as_snake_case() {
        String json = Json.toJsonWithoutIndent(
                new GeminiFunctionCallingConfig(GeminiMode.ANY, List.of("getWeather", "getTime")));

        assertThat(json)
                .contains("\"allowed_function_names\":[\"getWeather\",\"getTime\"]")
                .doesNotContain("allowedFunctionNames");
    }

    @Test
    void should_leave_every_other_field_camel_case() {
        GeminiGenerationConfig config = GeminiGenerationConfig.builder()
                .maxOutputTokens(100)
                .candidateCount(1)
                .responseMimeType("application/json")
                .stopSequences(List.of("STOP"))
                .build();

        String json = Json.toJsonWithoutIndent(config);

        assertThat(json)
                .contains("\"maxOutputTokens\":100")
                .contains("\"candidateCount\":1")
                .contains("\"responseMimeType\":\"application/json\"")
                .contains("\"stopSequences\":[\"STOP\"]");
        assertThat(json)
                .doesNotContain("max_output_tokens")
                .doesNotContain("candidate_count")
                .doesNotContain("response_mime_type");
    }

    @Test
    void should_write_a_safety_setting_with_its_two_single_word_fields() {
        String json = Json.toJsonWithoutIndent(new GeminiSafetySetting(
                GeminiHarmCategory.HARM_CATEGORY_HARASSMENT, GeminiHarmBlockThreshold.BLOCK_ONLY_HIGH));

        assertThat(json)
                .contains("\"category\":\"HARM_CATEGORY_HARASSMENT\"")
                .contains("\"threshold\":\"BLOCK_ONLY_HIGH\"");
    }
}
