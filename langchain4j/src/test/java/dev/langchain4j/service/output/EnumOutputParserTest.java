package dev.langchain4j.service.output;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
        assertEquals("\n" +
                "You must answer strictly with one of these enums:\n" +
                "SUNNY\n" +
                "CLOUDY\n" +
                "RAINY\n" +
                "SNOWY", instruction);
    }

    @Test
    public void parseResponse() {
        // When
        Enum<?> resultedEnum = sut.parse(Weather.SUNNY.name());

        // Then
        assertEquals(Weather.SUNNY, resultedEnum);
    }

    @Test
    public void parseResponseWithSpaces() {
        // When
        Enum<?> resultedEnum = sut.parse(" " + Weather.SUNNY.name() + "    ");

        // Then
        assertEquals(Weather.SUNNY, resultedEnum);
    }

    @Test
    public void parseResponseWithBrackets() {
        // When
        Enum<?> resultedEnum = sut.parse(" [  " + Weather.SUNNY.name() + "  ]  ");

        // Then
        assertEquals(Weather.SUNNY, resultedEnum);
    }
}