package dev.langchain4j.service.output;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Text that opens a JSON object but does not parse must surface as an
 * {@link OutputParsingException}, not as the deserializer's own exception.
 *
 * <p>The JSON array path already guards against this; the object path did not, so a malformed
 * object leaked a raw {@code RuntimeException} wrapping a Jackson parse error out of
 * {@link OutputParser#parse(String)}.
 */
class ParsingUtilsMalformedJsonTest {

    enum Color {
        RED,
        GREEN
    }

    // ===== Scalar parsers =====

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"value\": tru",
                "{oops}",
                "{",
                "{\"value\": }",
                "  {\"value\"  ",
            })
    @DisplayName("a malformed JSON object throws OutputParsingException, not a raw parse error")
    void malformed_json_object_throws_output_parsing_exception(String text) {
        assertThatThrownBy(() -> new BooleanOutputParser().parse(text))
                .isExactlyInstanceOf(OutputParsingException.class);
    }

    @Test
    @DisplayName("every scalar parser reports the documented exception type")
    void scalar_parsers_report_output_parsing_exception() {
        String malformed = "{\"value\": 1";

        assertThatThrownBy(() -> new BooleanOutputParser().parse(malformed))
                .isExactlyInstanceOf(OutputParsingException.class);
        assertThatThrownBy(() -> new IntegerOutputParser().parse(malformed))
                .isExactlyInstanceOf(OutputParsingException.class);
        assertThatThrownBy(() -> new LongOutputParser().parse(malformed))
                .isExactlyInstanceOf(OutputParsingException.class);
        assertThatThrownBy(() -> new DoubleOutputParser().parse(malformed))
                .isExactlyInstanceOf(OutputParsingException.class);
        assertThatThrownBy(() -> new FloatOutputParser().parse(malformed))
                .isExactlyInstanceOf(OutputParsingException.class);
        assertThatThrownBy(() -> new EnumOutputParser<>(Color.class).parse(malformed))
                .isExactlyInstanceOf(OutputParsingException.class);
    }

    @Test
    @DisplayName("the original parse failure is retained as the cause")
    void original_failure_is_kept_as_the_cause() {
        assertThatThrownBy(() -> new BooleanOutputParser().parse("{\"value\": tru"))
                .isExactlyInstanceOf(OutputParsingException.class)
                .hasCauseInstanceOf(RuntimeException.class);
    }

    // ===== Collection parsers =====

    @Test
    @DisplayName("collection parsers report OutputParsingException for a malformed object")
    void collection_parsers_report_output_parsing_exception() {
        String malformed = "{\"values\": [oops";

        assertThatThrownBy(() -> new StringListOutputParser().parse(malformed))
                .isExactlyInstanceOf(OutputParsingException.class);
        assertThatThrownBy(() -> new StringSetOutputParser().parse(malformed))
                .isExactlyInstanceOf(OutputParsingException.class);
    }

    // ===== Guards: valid and non-object input is unaffected =====

    @Test
    @DisplayName("a well-formed JSON object still parses")
    void well_formed_json_object_still_parses() {
        assertThat(new BooleanOutputParser().parse("{\"value\": true}")).isTrue();
        assertThat(new IntegerOutputParser().parse("{\"value\": 42}")).isEqualTo(42);
        assertThat(new StringListOutputParser().parse("{\"values\": [\"a\", \"b\"]}"))
                .isEqualTo(List.of("a", "b"));
    }

    @Test
    @DisplayName("plain (non-JSON) text still parses")
    void plain_text_still_parses() {
        assertThat(new BooleanOutputParser().parse("true")).isTrue();
        assertThat(new IntegerOutputParser().parse("42")).isEqualTo(42);
        assertThat(new StringListOutputParser().parse("a\nb")).isEqualTo(List.of("a", "b"));
    }

    @Test
    @DisplayName("the JSON array fallback is untouched")
    void json_array_fallback_is_untouched() {
        // Looks like an array but is not one; the existing behaviour is to treat it as lines.
        assertThat(new StringListOutputParser().parse("[apple]\n[banana]")).isEqualTo(List.of("[apple]", "[banana]"));
        assertThat(new StringSetOutputParser().parse("{\"values\": [\"a\"]}")).isEqualTo(Set.of("a"));
    }
}
