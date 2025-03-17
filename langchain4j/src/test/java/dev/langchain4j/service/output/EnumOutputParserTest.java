package dev.langchain4j.service.output;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EnumOutputParserTest {

    EnumOutputParser sut = new EnumOutputParser(Weather.class);

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
