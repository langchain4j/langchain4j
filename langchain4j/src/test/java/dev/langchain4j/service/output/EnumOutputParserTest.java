package dev.langchain4j.service.output;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static dev.langchain4j.service.output.EnumOutputParserTest.Animal.CAT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnumOutputParserTest {

    enum Animal {
        CAT, DOG, BIRD
    }

    @ParameterizedTest
    @MethodSource
    void should_parse_enum(String text, Animal expected) {

        // given
        EnumOutputParser parser = new EnumOutputParser(Animal.class);

        // when
        Animal animal = (Animal) parser.parse(text);

        // then
        assertThat(animal).isEqualTo(expected);
    }

    static Stream<Arguments> should_parse_enum() {
        return Stream.of(

            // Plain text
            Arguments.of("CAT", CAT),

            // Plain text: wrong case
            Arguments.of("cat", CAT),
            Arguments.of("Cat", CAT),

            // Plain text: empty
            Arguments.of(null, null),
            Arguments.of("", null),
            Arguments.of(" ", null),

            // Plain text: surrounded by whitespaces
            Arguments.of(" CAT ", CAT),

            // Plain text: surrounded by brackets
            Arguments.of("[CAT]", CAT),
            Arguments.of(" [ CAT ] ", CAT),

            // JSON
            Arguments.of("{\"value\":CAT}", CAT),
            Arguments.of("{\"value\":'CAT'}", CAT),
            Arguments.of("{\"value\":\"CAT\"}", CAT),

            // JSON: wrong case
            Arguments.of("{\"value\":cat}", CAT),
            Arguments.of("{\"value\":Cat}", CAT),

            // JSON: empty
            Arguments.of("{}", null),
            Arguments.of("{\"value\":null}", null),
            Arguments.of("{\"value\":\"\"}", null),

            // JSON: wrong property name
            Arguments.of("{\"animal\":CAT}", CAT),

            // JSON: surrounded by whitespaces
            Arguments.of(" {\"value\":CAT} ", CAT)
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "BANANA",
        "{\"value\":BANANA}"
    })
    void should_fail_to_parse_enum(String text) {

        // given
        EnumOutputParser parser = new EnumOutputParser(Animal.class);

        // when-then
        assertThatThrownBy(() -> parser.parse(text))
            .isExactlyInstanceOf(RuntimeException.class)
            .hasMessageContaining("Unknown enum value: BANANA");
    }

    enum Weather {
        SUNNY,
        CLOUDY,
        RAINY,
        SNOWY
    }

    @Test
    void test_formatInstructions() {

        // given
        EnumOutputParser parser = new EnumOutputParser(Weather.class);

        // when
        String instruction = parser.formatInstructions();

        // then
        assertThat(instruction).isEqualTo("\n" +
            "You must answer strictly with one of these enums:\n" +
            "SUNNY\n" +
            "CLOUDY\n" +
            "RAINY\n" +
            "SNOWY");
    }
}
