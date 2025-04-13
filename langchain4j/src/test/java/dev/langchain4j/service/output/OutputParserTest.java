package dev.langchain4j.service.output;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Calendar;
import java.util.Date;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

class OutputParserTest implements WithAssertions {

    @Test
    void big_decimal() {
        BigDecimalOutputParser parser = new BigDecimalOutputParser();
        assertThat(parser.formatInstructions()).isEqualTo("floating point number");

        assertThat(parser.parse("3.14")).isEqualTo(new BigDecimal("3.14"));
        assertThat(parser.parse(" 3.14 ")).isEqualTo(new BigDecimal("3.14"));

        assertThatExceptionOfType(NumberFormatException.class).isThrownBy(() -> parser.parse("3.14.15"));
    }

    @Test
    void big_integer() {
        BigIntegerOutputParser parser = new BigIntegerOutputParser();
        assertThat(parser.formatInstructions()).isEqualTo("integer number");

        assertThat(parser.parse("42")).isEqualTo(42);
        assertThat(parser.parse(" 42 ")).isEqualTo(42);

        assertThatExceptionOfType(NumberFormatException.class).isThrownBy(() -> parser.parse("42.0"));
    }

    @Test
    void test_Byte() {
        ByteOutputParser parser = new ByteOutputParser();
        assertThat(parser.formatInstructions()).isEqualTo("integer number in range [-128, 127]");

        assertThat(parser.parse("42")).isEqualTo((byte) 42);
        assertThat(parser.parse(" 42 ")).isEqualTo((byte) 42);
        assertThat(parser.parse("-42")).isEqualTo((byte) -42);

        assertThatExceptionOfType(NumberFormatException.class).isThrownBy(() -> parser.parse("42.0"));
    }

    @Test
    @SuppressWarnings("deprecation")
    void date() {
        DateOutputParser parser = new DateOutputParser();
        assertThat(parser.formatInstructions()).isEqualTo("yyyy-MM-dd");

        assertThat(parser.parse("2020-01-12"))
                .isEqualTo(parser.parse("2020-01-12"))
                .isEqualTo(parser.parse(" 2020-01-12 "))
                .isEqualTo(new Date(120, Calendar.JANUARY, 12));

        assertThatExceptionOfType(RuntimeException.class)
                .isThrownBy(() -> parser.parse("01-12-2020"))
                .withMessage("Invalid date format: 01-12-2020");
    }

    @Test
    void local_date() {
        LocalDateOutputParser parser = new LocalDateOutputParser();
        assertThat(parser.formatInstructions()).isEqualTo("yyyy-MM-dd");

        assertThat(parser.parse("2020-01-12"))
                .isEqualTo(parser.parse(" 2020-01-12 "))
                .isEqualTo(LocalDate.of(2020, 1, 12));

        assertThatExceptionOfType(RuntimeException.class).isThrownBy(() -> parser.parse("01-12-2020"));

        assertThatExceptionOfType(RuntimeException.class).isThrownBy(() -> parser.parse("red"));
    }

    @Test
    void local_date_time() {
        LocalDateTimeOutputParser parser = new LocalDateTimeOutputParser();
        assertThat(parser.formatInstructions()).isEqualTo("yyyy-MM-ddTHH:mm:ss");

        assertThat(parser.parse("2020-01-12T12:34:56"))
                .isEqualTo(parser.parse(" 2020-01-12T12:34:56 "))
                .isEqualTo(LocalDateTime.of(2020, 1, 12, 12, 34, 56));

        assertThatExceptionOfType(RuntimeException.class).isThrownBy(() -> parser.parse("01-12-2020T12:34:56"));

        assertThatExceptionOfType(RuntimeException.class).isThrownBy(() -> parser.parse("red"));
    }

    @Test
    void local_time() {
        LocalTimeOutputParser parser = new LocalTimeOutputParser();
        assertThat(parser.formatInstructions()).isEqualTo("HH:mm:ss");

        assertThat(parser.parse("12:34:56"))
                .isEqualTo(parser.parse(" 12:34:56 "))
                .isEqualTo(LocalTime.of(12, 34, 56));

        assertThat(parser.parse("12:34:56.789")).isEqualTo(LocalTime.of(12, 34, 56, 789_000_000));

        assertThatExceptionOfType(RuntimeException.class).isThrownBy(() -> parser.parse("red"));
    }

    @Test
    void test_Short() {
        ShortOutputParser parser = new ShortOutputParser();
        assertThat(parser.formatInstructions()).isEqualTo("integer number in range [-32768, 32767]");

        assertThat(parser.parse("42")).isEqualTo((short) 42);
        assertThat(parser.parse(" 42 ")).isEqualTo((short) 42);
        assertThat(parser.parse("-42")).isEqualTo((short) -42);

        assertThatExceptionOfType(NumberFormatException.class).isThrownBy(() -> parser.parse("42.0"));
    }
}
