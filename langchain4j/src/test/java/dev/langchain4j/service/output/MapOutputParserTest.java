package dev.langchain4j.service.output;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class MapOutputParserTest {

    private final MapOutputParser parser = new MapOutputParser();

    @Test
    void should_parse_json_object() {
        Map<String, Object> result = parser.parse("{\"capital\":\"Paris\",\"population\":2100000}");

        assertThat(result).isEqualTo(Map.of("capital", "Paris", "population", 2100000));
    }

    @Test
    void should_parse_empty_object() {
        assertThat(parser.parse("{}")).isEmpty();
    }

    @Test
    void should_parse_nested_json_and_fenced_block() {
        String fenced = "Here is the metadata:\n```json\n{\"tags\":[\"a\",\"b\"],\"ok\":true}\n```\nDone.";

        Map<String, Object> result = parser.parse(fenced);

        assertThat(result).containsEntry("ok", true);
        assertThat(result.get("tags")).isEqualTo(List.of("a", "b"));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "capital of France?", "null", "[1,2]", "{\"capital\":\"Paris\"", "{"})
    void should_fail_when_text_is_not_a_json_object(String text) {
        assertThatThrownBy(() -> parser.parse(text))
                .isExactlyInstanceOf(OutputParsingException.class)
                .hasMessageContaining("java.util.Map");
    }

    @Test
    void format_instructions_require_json() {
        assertThat(parser.formatInstructions()).isEqualTo(MapOutputParser.FORMAT_INSTRUCTIONS);
        assertThat(parser.formatInstructions()).contains("JSON");
        assertThat(parser.jsonSchema()).isEmpty();
    }
}
