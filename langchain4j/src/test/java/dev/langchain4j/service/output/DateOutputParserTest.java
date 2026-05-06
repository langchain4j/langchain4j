package dev.langchain4j.service.output;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.format.DateTimeParseException;
import java.util.Calendar;
import java.util.Date;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DateOutputParserTest {

    private final DateOutputParser parser = new DateOutputParser();

    @ParameterizedTest
    @MethodSource
    void should_parse_valid_input(String input, int year, int month, int day) {

        // when
        Date actual = parser.parse(input);

        // then
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(actual);
        assertThat(calendar.get(Calendar.YEAR)).isEqualTo(year);
        assertThat(calendar.get(Calendar.MONTH)).isEqualTo(month - 1);
        assertThat(calendar.get(Calendar.DAY_OF_MONTH)).isEqualTo(day);
    }

    static Stream<Arguments> should_parse_valid_input() {
        return Stream.of(
                Arguments.of("2024-01-15", 2024, 1, 15),
                Arguments.of("2000-12-31", 2000, 12, 31),
                Arguments.of("1999-06-01", 1999, 6, 1),
                Arguments.of("  2024-01-15  ", 2024, 1, 15)
        );
    }

    @Test
    void should_fail_to_parse_null_input() {

        assertThatThrownBy(() -> parser.parse(null))
                .isExactlyInstanceOf(OutputParsingException.class)
                .hasMessage("Cannot parse null into java.util.Date")
                .hasNoCause();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            " ",
            "15-01-2024",
            "01-15-2024",
            "2024/01/15",
            "20240115",
            "not-a-date",
            "2024-1-5"
    })
    void should_fail_to_parse_invalid_input(String input) {

        assertThatThrownBy(() -> parser.parse(input))
                .isExactlyInstanceOf(OutputParsingException.class)
                .hasMessageContainingAll("Cannot parse", input, "into java.util.Date")
                .hasCauseExactlyInstanceOf(DateTimeParseException.class);
    }

    @Test
    void format_instructions() {

        // when
        String instructions = parser.formatInstructions();

        // then
        assertThat(instructions).isEqualTo("yyyy-MM-dd");
    }
}
