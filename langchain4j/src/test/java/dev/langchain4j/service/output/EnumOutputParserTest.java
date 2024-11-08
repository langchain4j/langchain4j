package dev.langchain4j.service.output;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EnumOutputParserTest {

    EnumOutputParser sut = new EnumOutputParser(Weather.class);

    enum Weather {
        SUNNY,
        CLOUDY,
        RAINY,
        SNOWY
    }

    @Test
    public void generateInstruction() {
        // When
        String instruction = sut.formatInstructions();

        // Then
        assertThat(instruction).isEqualTo("\n" +
                "You must answer strictly with one of these enums:\n" +
                "SUNNY\n" +
                "CLOUDY\n" +
                "RAINY\n" +
                "SNOWY");
    }

    @Test
    public void parseResponse() {
        // When
        Enum<?> resultedEnum = sut.parse(Weather.SUNNY.name());

        // Then
        assertThat(resultedEnum).isEqualTo(Weather.SUNNY);
    }

    @Test
    public void parseResponseWithSpaces() {
        // When
        Enum<?> resultedEnum = sut.parse(" " + Weather.SUNNY.name() + "    ");

        // Then
        assertThat(resultedEnum).isEqualTo(Weather.SUNNY);
    }

    @Test
    public void parseResponseWithBrackets() {
        // When
        Enum<?> resultedEnum = sut.parse(" [  " + Weather.SUNNY.name() + "  ]  ");

        // Then
        assertThat(resultedEnum).isEqualTo(Weather.SUNNY);
    }
}