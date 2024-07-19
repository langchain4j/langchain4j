package dev.langchain4j.service.output;

import dev.langchain4j.model.output.structured.Description;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Calendar;
import java.util.Date;

class OutputParserTest implements WithAssertions {

    @Test
    public void test_BigDecimal() {
        BigDecimalOutputParser parser = new BigDecimalOutputParser();
        assertThat(parser.formatInstructions())
                .isEqualTo("floating point number");

        assertThat(parser.parse("3.14")).isEqualTo(new BigDecimal("3.14"));
        assertThat(parser.parse(" 3.14 ")).isEqualTo(new BigDecimal("3.14"));

        assertThatExceptionOfType(NumberFormatException.class)
                .isThrownBy(() -> parser.parse("3.14.15"));
    }

    @Test
    public void test_BigInteger() {
        BigIntegerOutputParser parser = new BigIntegerOutputParser();
        assertThat(parser.formatInstructions())
                .isEqualTo("integer number");

        assertThat(parser.parse("42")).isEqualTo(42);
        assertThat(parser.parse(" 42 ")).isEqualTo(42);

        assertThatExceptionOfType(NumberFormatException.class)
                .isThrownBy(() -> parser.parse("42.0"));
    }

    @Test
    public void test_Boolean() {
        BooleanOutputParser parser = new BooleanOutputParser();
        assertThat(parser.formatInstructions())
                .isEqualTo("one of [true, false]");

        assertThat(parser.parse("true"))
                .isEqualTo(parser.parse(" true "))
                .isEqualTo(parser.parse("TRUE"))
                .isEqualTo(true);
        assertThat(parser.parse("false"))
                .isEqualTo(parser.parse("FALSE"))
                // surprising, but how Boolean.parseBoolean works
                .isEqualTo(parser.parse("yes"))
                .isEqualTo(false);
    }

    @Test
    public void test_Byte() {
        ByteOutputParser parser = new ByteOutputParser();
        assertThat(parser.formatInstructions())
                .isEqualTo("integer number in range [-128, 127]");

        assertThat(parser.parse("42")).isEqualTo((byte) 42);
        assertThat(parser.parse(" 42 ")).isEqualTo((byte) 42);
        assertThat(parser.parse("-42")).isEqualTo((byte) -42);

        assertThatExceptionOfType(NumberFormatException.class)
                .isThrownBy(() -> parser.parse("42.0"));
    }

    @Test
    @SuppressWarnings("deprecation")
    public void test_Date() {
        DateOutputParser parser = new DateOutputParser();
        assertThat(parser.formatInstructions())
                .isEqualTo("yyyy-MM-dd");

        assertThat(parser.parse("2020-01-12"))
                .isEqualTo(parser.parse("2020-01-12"))
                .isEqualTo(parser.parse(" 2020-01-12 "))
                .isEqualTo(new Date(120, Calendar.JANUARY, 12));

        assertThatExceptionOfType(RuntimeException.class)
                .isThrownBy(() -> parser.parse("01-12-2020"))
                .withMessage("Invalid date format: 01-12-2020");
    }

    @Test
    public void test_Double() {
        DoubleOutputParser parser = new DoubleOutputParser();
        assertThat(parser.formatInstructions())
                .isEqualTo("floating point number");

        assertThat(parser.parse("3.14")).isEqualTo(3.14);
        assertThat(parser.parse(" 3.14 ")).isEqualTo(3.14);

        assertThatExceptionOfType(NumberFormatException.class)
                .isThrownBy(() -> parser.parse("3.14.15"));
    }

    public enum Enum {
        A, B, C
    }

    public enum EnumWithDescription {
        @Description("Majority of keywords starting with A")
        A,
        @Description("Majority of keywords starting with B")
        B,
        @Description("Majority of keywords starting with C")
        C
    }

    @Test
    @SuppressWarnings("unchecked")
    public void test_Enum_format_instruction() {
        EnumOutputParser parser = new EnumOutputParser(Enum.class);
        assertThat(parser.formatInstructions())
                .isEqualTo("\nYou must answer strictly with one of these enums:\n" +
                        "A\n" +
                        "B\n" +
                        "C");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void test_Enum_with_description_format_instruction() {
        EnumOutputParser parser = new EnumOutputParser(EnumWithDescription.class);
        assertThat(parser.formatInstructions())
                .isEqualTo("\nYou must answer strictly with one of these enums:\n" +
                        "A - Majority of keywords starting with A\n" +
                        "B - Majority of keywords starting with B\n" +
                        "C - Majority of keywords starting with C");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void test_Enum() {
        EnumOutputParser parser = new EnumOutputParser(Enum.class);

        assertThat(parser.parse("A"))
                .isEqualTo(parser.parse(" A "))
                .isEqualTo(parser.parse("a"))
                .isEqualTo(Enum.A);
        assertThat(parser.parse("B"))
                .isEqualTo(parser.parse("b"))
                .isEqualTo(Enum.B);
        assertThat(parser.parse("C"))
                .isEqualTo(parser.parse("c"))
                .isEqualTo(Enum.C);

        assertThatExceptionOfType(RuntimeException.class)
                .isThrownBy(() -> parser.parse("D"))
                .withMessage("Unknown enum value: D");
    }

    @Test
    public void test_Float() {
        FloatOutputParser parser = new FloatOutputParser();
        assertThat(parser.formatInstructions())
                .isEqualTo("floating point number");

        assertThat(parser.parse("3.14")).isEqualTo(3.14f);
        assertThat(parser.parse(" 3.14 ")).isEqualTo(3.14f);

        assertThatExceptionOfType(NumberFormatException.class)
                .isThrownBy(() -> parser.parse("3.14.15"));
    }

    @Test
    public void test_Integer() {
        IntOutputParser parser = new IntOutputParser();
        assertThat(parser.formatInstructions())
                .isEqualTo("integer number");

        assertThat(parser.parse("42")).isEqualTo(42);
        assertThat(parser.parse(" 42 ")).isEqualTo(42);

        assertThatExceptionOfType(NumberFormatException.class)
                .isThrownBy(() -> parser.parse("42.0"));
    }

    @Test
    public void test_LocalDate() {
        LocalDateOutputParser parser = new LocalDateOutputParser();
        assertThat(parser.formatInstructions())
                .isEqualTo("yyyy-MM-dd");

        assertThat(parser.parse("2020-01-12"))
                .isEqualTo(parser.parse(" 2020-01-12 "))
                .isEqualTo(LocalDate.of(2020, 1, 12));

        assertThatExceptionOfType(RuntimeException.class)
                .isThrownBy(() -> parser.parse("01-12-2020"));

        assertThatExceptionOfType(RuntimeException.class)
                .isThrownBy(() -> parser.parse("red"));
    }

    @Test
    public void test_LocalDateTime() {
        LocalDateTimeOutputParser parser = new LocalDateTimeOutputParser();
        assertThat(parser.formatInstructions())
                .isEqualTo("yyyy-MM-ddTHH:mm:ss");

        assertThat(parser.parse("2020-01-12T12:34:56"))
                .isEqualTo(parser.parse(" 2020-01-12T12:34:56 "))
                .isEqualTo(LocalDateTime.of(2020, 1, 12, 12, 34, 56));

        assertThatExceptionOfType(RuntimeException.class)
                .isThrownBy(() -> parser.parse("01-12-2020T12:34:56"));

        assertThatExceptionOfType(RuntimeException.class)
                .isThrownBy(() -> parser.parse("red"));
    }

    @Test
    public void test_LocalTime() {
        LocalTimeOutputParser parser = new LocalTimeOutputParser();
        assertThat(parser.formatInstructions())
                .isEqualTo("HH:mm:ss");

        assertThat(parser.parse("12:34:56"))
                .isEqualTo(parser.parse(" 12:34:56 "))
                .isEqualTo(LocalTime.of(12, 34, 56));

        assertThat(parser.parse("12:34:56.789"))
                .isEqualTo(LocalTime.of(12, 34, 56, 789_000_000));

        assertThatExceptionOfType(RuntimeException.class)
                .isThrownBy(() -> parser.parse("red"));
    }

    @Test
    public void test_Long() {
        LongOutputParser parser = new LongOutputParser();
        assertThat(parser.formatInstructions())
                .isEqualTo("integer number");

        assertThat(parser.parse("42")).isEqualTo(42L);
        assertThat(parser.parse(" 42 ")).isEqualTo(42L);

        assertThatExceptionOfType(NumberFormatException.class)
                .isThrownBy(() -> parser.parse("42.0"));
    }

    @Test
    public void test_Short() {
        ShortOutputParser parser = new ShortOutputParser();
        assertThat(parser.formatInstructions())
                .isEqualTo("integer number in range [-32768, 32767]");

        assertThat(parser.parse("42")).isEqualTo((short) 42);
        assertThat(parser.parse(" 42 ")).isEqualTo((short) 42);
        assertThat(parser.parse("-42")).isEqualTo((short) -42);

        assertThatExceptionOfType(NumberFormatException.class)
                .isThrownBy(() -> parser.parse("42.0"));
    }
}