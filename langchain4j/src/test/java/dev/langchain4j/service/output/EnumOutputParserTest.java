package dev.langchain4j.service.output;

import dev.langchain4j.model.output.structured.Description;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static dev.langchain4j.service.output.EnumOutputParserTest.Animal.CAT;
import static dev.langchain4j.service.output.EnumOutputParserTest.Weather.SUNNY;
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
                Arguments.of("cat", CAT),
                Arguments.of("Cat", CAT),
                Arguments.of(" CAT ", CAT),
                Arguments.of("[CAT]", CAT),
                Arguments.of("  [ CAT ]  ", CAT),

                // JSON
                Arguments.of("{\"value\":\"CAT\"}", CAT),
                Arguments.of("  {\"value\":\"CAT\"}  ", CAT),
                Arguments.of("  {\"value\":[\"CAT\"]}  ", CAT)
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
            "{\"value\":\"BANANA\"}",
    })
    void should_fail_to_parse_invalid_input(String text) {

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

        assertThat(sut.formatInstructions())
                .isEqualTo("\n" + "You must answer strictly with one of these enums:\n"
                        + "SUNNY\n"
                        + "CLOUDY\n"
                        + "RAINY\n"
                        + "SNOWY");
    }

    @Test
    void parseResponse() {

        // When
        Weather weather = sut.parse(SUNNY.name());

        // Then
        assertThat(weather).isEqualTo(SUNNY);
    }

    @Test
    void parseResponseWithSpaces() {

        // When
        Weather weather = sut.parse(" " + SUNNY.name() + "    ");

        // Then
        assertThat(weather).isEqualTo(SUNNY);
    }

    @Test
    void parseResponseWithBrackets() {

        // When
        Weather weather = sut.parse(" [  " + SUNNY.name() + "  ]  ");

        // Then
        assertThat(weather).isEqualTo(SUNNY);
    }

    public enum Category {
        A,
        B,
        C
    }

    public enum CategoryWithDescription {
        @Description("Majority of keywords starting with A")
        A,
        @Description("Majority of keywords starting with B")
        B,
        @Description("Majority of keywords starting with C")
        C
    }

    @Test
    void enum_format_instruction() {
        EnumOutputParser<Category> parser = new EnumOutputParser<>(Category.class);
        assertThat(parser.formatInstructions())
                .isEqualTo("\nYou must answer strictly with one of these enums:\n" + "A\n" + "B\n" + "C");
    }

    @Test
    void enum_with_description_format_instruction() {
        EnumOutputParser<CategoryWithDescription> parser = new EnumOutputParser<>(CategoryWithDescription.class);
        assertThat(parser.formatInstructions())
                .isEqualTo("\nYou must answer strictly with one of these enums:\n"
                        + "A - Majority of keywords starting with A\n"
                        + "B - Majority of keywords starting with B\n"
                        + "C - Majority of keywords starting with C");
    }

}
