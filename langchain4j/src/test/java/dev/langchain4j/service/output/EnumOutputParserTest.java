package dev.langchain4j.service.output;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static dev.langchain4j.service.output.EnumOutputParserTest.Animal.CAT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnumOutputParserTest {

    EnumOutputParser<Weather> sut = new EnumOutputParser<>(Weather.class);

    enum Animal {
        CAT, DOG, BIRD
    }

    @ParameterizedTest
    @MethodSource
    void should_parse_enum(String text, Animal expected) {

        // given
        EnumOutputParser<Animal> parser = new EnumOutputParser<>(Animal.class);

        // when
        Animal animal = parser.parse(text);

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

                // Plain text: surrounded by whitespaces
                Arguments.of(" CAT ", CAT),

                // Plain text: surrounded by brackets
                Arguments.of("[CAT]", CAT),
                Arguments.of(" [ CAT ] ", CAT),

                // JSON
                Arguments.of("{\"value\":\"CAT\"}", CAT),

                // JSON: wrong property name
                Arguments.of("{\"animal\":\"CAT\"}", CAT),

                // JSON: surrounded by whitespaces
                Arguments.of(" {\"value\":\"CAT\"} ", CAT)
        );
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "{}", "{\"value\": null}", "{\"value\": \"\"}"})
    void should_fail_to_parse_empty_input(String input) {

        assertThatThrownBy(() -> new EnumOutputParser<>(Animal.class).parse(input))
                .isExactlyInstanceOf(OutputParsingException.class)
                .hasMessageContaining("Failed to parse")
                .hasMessageContaining("Animal");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "BANANA",
            "{\"value\":\"BANANA\"}"
    })
    void should_fail_to_parse_enum(String text) {

        // given
        EnumOutputParser<Animal> parser = new EnumOutputParser<>(Animal.class);

        // when-then
        assertThatThrownBy(() -> parser.parse(text))
                .isExactlyInstanceOf(OutputParsingException.class)
                .hasMessageContaining("Failed to parse")
                .hasMessageContaining("Animal");
    }

    enum Weather {
        SUNNY,
        CLOUDY,
        RAINY,
        SNOWY
    }

    @Test
    void generateInstruction() {
        // When
        String instruction = sut.formatInstructions();

        // Then
        assertThat(instruction)
                .isEqualTo("\n" + "You must answer strictly with one of these enums:\n"
                        + "SUNNY\n"
                        + "CLOUDY\n"
                        + "RAINY\n"
                        + "SNOWY");
    }

    @Test
    void parseResponse() {
        // When
        Enum<?> resultedEnum = sut.parse(Weather.SUNNY.name());

        // Then
        assertThat(resultedEnum).isEqualTo(Weather.SUNNY);
    }

    @Test
    void parseResponseWithSpaces() {
        // When
        Enum<?> resultedEnum = sut.parse(" " + Weather.SUNNY.name() + "    ");

        // Then
        assertThat(resultedEnum).isEqualTo(Weather.SUNNY);
    }

    @Test
    void parseResponseWithBrackets() {
        // When
        Enum<?> resultedEnum = sut.parse(" [  " + Weather.SUNNY.name() + "  ]  ");

        // Then
        assertThat(resultedEnum).isEqualTo(Weather.SUNNY);
    }
}
